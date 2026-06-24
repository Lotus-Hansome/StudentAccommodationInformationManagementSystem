package com.dormitory;

public class ModelConfigProfile {
    private final String id;
    private final String name;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public ModelConfigProfile(String id, String name, String apiUrl, String apiKey, String model) {
        this.id = safe(id);
        this.name = safe(name);
        this.apiUrl = safe(apiUrl);
        this.apiKey = safe(apiKey);
        this.model = safe(model);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public boolean isConfigured() {
        return !apiUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    public String maskedApiKey() {
        if (apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    public ModelServiceConfig toServiceConfig(String source) {
        return new ModelServiceConfig(apiUrl, apiKey, model, source);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
