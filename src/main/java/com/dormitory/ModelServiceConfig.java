package com.dormitory;

public class ModelServiceConfig {
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final String source;

    public ModelServiceConfig(String apiUrl, String apiKey, String model, String source) {
        this.apiUrl = safe(apiUrl);
        this.apiKey = safe(apiKey);
        this.model = safe(model);
        this.source = safe(source);
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

    public String getSource() {
        return source;
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
