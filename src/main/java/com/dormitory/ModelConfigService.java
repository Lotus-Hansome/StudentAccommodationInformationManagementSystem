package com.dormitory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

public class ModelConfigService {
    private static final Path CONFIG_PATH = Path.of("config", "model.properties");
    private static final String ACTIVE_ID_KEY = "model.active.id";
    private static final String PROFILE_IDS_KEY = "model.config.ids";
    private static final String LEGACY_PROFILE_ID = "default";

    public ModelServiceConfig loadEffectiveConfig() {
        return loadStatus().getEffectiveConfig();
    }

    public ModelConfigStatus loadStatus() {
        Properties properties = loadProperties();
        boolean localConfigPresent = Files.exists(CONFIG_PATH);
        ModelServiceConfig environmentConfig = loadEnvironmentConfig();
        List<ModelConfigProfile> profiles = loadProfiles(properties);
        String activeId = determineActiveId(properties, profiles, localConfigPresent);
        boolean localAnalysisSelected = localConfigPresent && activeId.isBlank();

        ModelServiceConfig effectiveConfig = new ModelServiceConfig("", "", "", "local_rule");
        if (!localAnalysisSelected && !activeId.isBlank()) {
            Optional<ModelConfigProfile> activeProfile = findProfile(profiles, activeId);
            if (activeProfile.isPresent() && activeProfile.get().isConfigured()) {
                effectiveConfig = activeProfile.get().toServiceConfig("local_file");
            }
        } else if (!localConfigPresent && environmentConfig.isConfigured()) {
            effectiveConfig = environmentConfig;
        }

        return new ModelConfigStatus(
                profiles,
                activeId,
                effectiveConfig,
                localConfigPresent,
                localAnalysisSelected || !effectiveConfig.isConfigured(),
                environmentConfig.isConfigured());
    }

    public ModelConfigStatus saveLocalConfig(String id, String name, String apiUrl, String apiKey, String model, boolean activate) {
        ModelConfigStatus current = loadStatus();
        List<ModelConfigProfile> profiles = new ArrayList<>(current.getProfiles());
        String profileId = safe(id).isBlank() ? newProfileId() : sanitizeId(id);
        ModelConfigProfile existing = findProfile(profiles, profileId).orElse(null);
        String effectiveKey = isBlank(apiKey) && existing != null ? existing.getApiKey() : safe(apiKey);
        ModelConfigProfile profile = new ModelConfigProfile(
                profileId,
                firstNonBlank(name, inferDisplayName(apiUrl, model, profileId)),
                apiUrl,
                effectiveKey,
                model);

        boolean replaced = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(profileId)) {
                profiles.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            profiles.add(profile);
        }

        String activeId = activate ? profileId : current.getActiveId();
        if (current.isLocalAnalysisSelected() && !activate) {
            activeId = "";
        }
        saveProfiles(profiles, activeId);
        return loadStatus();
    }

    public ModelConfigStatus selectConfig(String id) {
        String activeId = "local".equalsIgnoreCase(safe(id)) ? "" : sanitizeId(id);
        ModelConfigStatus current = loadStatus();
        if (!activeId.isBlank() && findProfile(current.getProfiles(), activeId).isEmpty()) {
            throw new IllegalArgumentException("未找到要启用的模型配置。");
        }
        saveProfiles(current.getProfiles(), activeId);
        return loadStatus();
    }

    public ModelConfigStatus deleteLocalConfig(String id) {
        String profileId = sanitizeId(id);
        ModelConfigStatus current = loadStatus();
        List<ModelConfigProfile> profiles = current.getProfiles().stream()
                .filter(profile -> !profile.getId().equals(profileId))
                .collect(Collectors.toCollection(ArrayList::new));
        String activeId = current.getActiveId().equals(profileId) ? "" : current.getActiveId();
        saveProfiles(profiles, activeId);
        return loadStatus();
    }

    public ModelConfigStatus clearLocalConfig() {
        saveProfiles(List.of(), "");
        return loadStatus();
    }

    public boolean hasLocalConfigFile() {
        return Files.exists(CONFIG_PATH);
    }

    public boolean isLocalConfigured() {
        return loadStatus().getProfiles().stream().anyMatch(ModelConfigProfile::isConfigured);
    }

    public boolean isEnvironmentConfigured() {
        return loadEnvironmentConfig().isConfigured();
    }

    private List<ModelConfigProfile> loadProfiles(Properties properties) {
        List<ModelConfigProfile> profiles = new ArrayList<>();
        String ids = properties.getProperty(PROFILE_IDS_KEY, "");
        for (String rawId : ids.split(",")) {
            String id = sanitizeId(rawId);
            if (id.isBlank()) {
                continue;
            }
            ModelConfigProfile profile = profileFromProperties(properties, id);
            if (!profile.getApiUrl().isBlank() || !profile.getModel().isBlank() || profile.hasApiKey()) {
                profiles.add(profile);
            }
        }

        boolean hasLegacyConfig = !safe(properties.getProperty("model.api.url")).isBlank()
                || !safe(properties.getProperty("model.name")).isBlank()
                || !safe(properties.getProperty("model.api.key")).isBlank();
        if (profiles.isEmpty() && hasLegacyConfig) {
            profiles.add(new ModelConfigProfile(
                    LEGACY_PROFILE_ID,
                    "默认模型服务",
                    properties.getProperty("model.api.url", ""),
                    properties.getProperty("model.api.key", ""),
                    properties.getProperty("model.name", "")));
        }
        return profiles;
    }

    private ModelConfigProfile profileFromProperties(Properties properties, String id) {
        String prefix = "model.config." + id + ".";
        return new ModelConfigProfile(
                id,
                properties.getProperty(prefix + "name", ""),
                properties.getProperty(prefix + "api.url", ""),
                properties.getProperty(prefix + "api.key", ""),
                properties.getProperty(prefix + "model", ""));
    }

    private String determineActiveId(Properties properties, List<ModelConfigProfile> profiles, boolean localConfigPresent) {
        if (!localConfigPresent) {
            return "";
        }
        if (properties.containsKey(ACTIVE_ID_KEY)) {
            return sanitizeId(properties.getProperty(ACTIVE_ID_KEY, ""));
        }
        if (profiles.size() == 1 && profiles.get(0).isConfigured()) {
            return profiles.get(0).getId();
        }
        return "";
    }

    private Optional<ModelConfigProfile> findProfile(List<ModelConfigProfile> profiles, String id) {
        return profiles.stream().filter(profile -> profile.getId().equals(id)).findFirst();
    }

    private void saveProfiles(List<ModelConfigProfile> profiles, String activeId) {
        Properties properties = new Properties();
        properties.setProperty(ACTIVE_ID_KEY, safe(activeId));
        properties.setProperty(PROFILE_IDS_KEY, profiles.stream()
                .map(ModelConfigProfile::getId)
                .collect(Collectors.joining(",")));
        for (ModelConfigProfile profile : profiles) {
            String prefix = "model.config." + profile.getId() + ".";
            properties.setProperty(prefix + "name", profile.getName());
            properties.setProperty(prefix + "api.url", profile.getApiUrl());
            properties.setProperty(prefix + "api.key", profile.getApiKey());
            properties.setProperty(prefix + "model", profile.getModel());
        }
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(outputStream, "Local model service configurations. Do not commit this file.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("保存模型服务配置失败：" + e.getMessage(), e);
        }
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            } catch (IOException e) {
                throw new IllegalStateException("读取模型服务配置失败：" + e.getMessage(), e);
            }
        }
        return properties;
    }

    private ModelServiceConfig loadEnvironmentConfig() {
        String apiUrl = firstNonBlank(System.getenv("LLM_API_URL"), toChatCompletionsUrl(System.getenv("OPENAI_BASE_URL")));
        String apiKey = firstNonBlank(System.getenv("LLM_API_KEY"), System.getenv("OPENAI_API_KEY"));
        String model = firstNonBlank(System.getenv("LLM_MODEL"), System.getenv("OPENAI_MODEL"));
        return new ModelServiceConfig(apiUrl, apiKey, model, "environment");
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

    private static String newProfileId() {
        return "cfg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String inferDisplayName(String apiUrl, String model, String fallback) {
        String text = (safe(apiUrl) + " " + safe(model)).toLowerCase();
        if (text.contains("deepseek")) {
            return "DeepSeek Compatible";
        }
        if (text.contains("openai")) {
            return "OpenAI Official";
        }
        if (text.contains("siliconflow")) {
            return "SiliconFlow";
        }
        if (text.contains("dashscope") || text.contains("aliyuncs")) {
            return "DashScope Compatible";
        }
        return fallback.isBlank() ? "模型服务配置" : fallback;
    }

    private static String sanitizeId(String value) {
        String id = safe(value).replaceAll("[^A-Za-z0-9_-]", "");
        return id.length() > 48 ? id.substring(0, 48) : id;
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? safe(second) : safe(first);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
