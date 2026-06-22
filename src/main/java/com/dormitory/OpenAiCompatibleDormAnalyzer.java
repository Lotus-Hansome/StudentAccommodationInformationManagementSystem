package com.dormitory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OpenAiCompatibleDormAnalyzer implements DormAnalyzer {
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;

    public OpenAiCompatibleDormAnalyzer() {
        this.apiUrl = firstNonBlank(System.getenv("LLM_API_URL"), toChatCompletionsUrl(System.getenv("OPENAI_BASE_URL")));
        this.apiKey = firstNonBlank(System.getenv("LLM_API_KEY"), System.getenv("OPENAI_API_KEY"));
        this.model = firstNonBlank(System.getenv("LLM_MODEL"), System.getenv("OPENAI_MODEL"));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return !isBlank(apiUrl) && !isBlank(apiKey) && !isBlank(model);
    }

    @Override
    public String analyze(DormStatistics statistics) {
        if (!isConfigured()) {
            throw new IllegalStateException("未配置 LLM_API_URL/LLM_API_KEY/LLM_MODEL。");
        }
        String prompt = "请根据以下学生宿舍统计数据，生成一段不超过120字的宿舍运营评估与建议。"
                + "要求指出床位是否紧张、各系入住结构是否需要关注，并给出管理建议。\n\n"
                + statistics.toPromptText();
        String body = "{"
                + "\"model\":\"" + escapeJson(model) + "\","
                + "\"temperature\":0.2,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"你是高校宿舍资源管理助手，回答要简洁、客观、可执行。\"},"
                + "{\"role\":\"user\",\"content\":\"" + escapeJson(prompt) + "\"}"
                + "]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("大模型接口返回异常状态码：" + response.statusCode());
            }
            String content = extractAssistantContent(response.body());
            if (isBlank(content)) {
                throw new IllegalStateException("大模型接口未返回可用内容。");
            }
            return content.trim();
        } catch (IOException e) {
            throw new IllegalStateException("大模型接口网络调用失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大模型接口调用被中断。", e);
        }
    }

    private static String extractAssistantContent(String json) {
        String key = "\"content\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            return "";
        }
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = quote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaping) {
                if (ch == 'u' && i + 4 < json.length()) {
                    String hex = json.substring(i + 1, i + 5);
                    try {
                        builder.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    } catch (NumberFormatException e) {
                        builder.append("\\u").append(hex);
                        i += 4;
                    }
                } else {
                    builder.append(switch (ch) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '"' -> '"';
                        case '\\' -> '\\';
                        default -> ch;
                    });
                }
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '"') {
                break;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String toChatCompletionsUrl(String baseUrl) {
        if (isBlank(baseUrl)) {
            return "";
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
