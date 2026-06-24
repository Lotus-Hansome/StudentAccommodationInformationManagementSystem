package com.dormitory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ModelConfigService {
    private static final Path CONFIG_PATH = Path.of("config", "model.properties");

    public ModelServiceConfig loadEffectiveConfig() {
        ModelServiceConfig fileConfig = loadFileConfig();
        if (fileConfig.isConfigured()) {
            return fileConfig;
        }
        ModelServiceConfig environmentConfig = loadEnvironmentConfig();
        return environmentConfig.isConfigured() ? environmentConfig : fileConfig;
    }

    public ModelServiceConfig loadStatusConfig() {
        ModelServiceConfig fileConfig = loadFileConfig();
        if (hasLocalConfigFile() || fileConfig.isConfigured()) {
            return fileConfig;
        }
        ModelServiceConfig environmentConfig = loadEnvironmentConfig();
        if (environmentConfig.isConfigured()) {
            return environmentConfig;
        }
        return fileConfig;
    }

    public ModelServiceConfig saveLocalConfig(String apiUrl, String apiKey, String model) {
        ModelServiceConfig existing = loadFileConfig();
        String effectiveKey = isBlank(apiKey) ? existing.getApiKey() : apiKey.trim();

        Properties properties = new Properties();
        properties.setProperty("model.api.url", safe(apiUrl));
        properties.setProperty("model.api.key", effectiveKey);
        properties.setProperty("model.name", safe(model));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(outputStream, "Local model service configuration. Do not commit this file.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("保存模型服务配置失败：" + e.getMessage(), e);
        }
        return loadFileConfig();
    }

    public ModelServiceConfig clearLocalConfig() {
        try {
            Files.deleteIfExists(CONFIG_PATH);
        } catch (IOException e) {
            throw new IllegalStateException("取消模型服务配置失败：" + e.getMessage(), e);
        }
        return loadStatusConfig();
    }

    public boolean hasLocalConfigFile() {
        return Files.exists(CONFIG_PATH);
    }

    public boolean isLocalConfigured() {
        return loadFileConfig().isConfigured();
    }

    public boolean isEnvironmentConfigured() {
        return loadEnvironmentConfig().isConfigured();
    }

    private ModelServiceConfig loadEnvironmentConfig() {
        String apiUrl = firstNonBlank(System.getenv("LLM_API_URL"), toChatCompletionsUrl(System.getenv("OPENAI_BASE_URL")));
        String apiKey = firstNonBlank(System.getenv("LLM_API_KEY"), System.getenv("OPENAI_API_KEY"));
        String model = firstNonBlank(System.getenv("LLM_MODEL"), System.getenv("OPENAI_MODEL"));
        return new ModelServiceConfig(apiUrl, apiKey, model, "environment");
    }

    private ModelServiceConfig loadFileConfig() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            } catch (IOException e) {
                throw new IllegalStateException("读取模型服务配置失败：" + e.getMessage(), e);
            }
        }
        return new ModelServiceConfig(
                properties.getProperty("model.api.url", ""),
                properties.getProperty("model.api.key", ""),
                properties.getProperty("model.name", ""),
                "local_file");
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
