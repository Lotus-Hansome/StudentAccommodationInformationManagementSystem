package com.dormitory;

import java.util.List;

public class ModelConfigStatus {
    private final List<ModelConfigProfile> profiles;
    private final String activeId;
    private final ModelServiceConfig effectiveConfig;
    private final boolean localConfigPresent;
    private final boolean localAnalysisSelected;
    private final boolean environmentConfigured;

    public ModelConfigStatus(
            List<ModelConfigProfile> profiles,
            String activeId,
            ModelServiceConfig effectiveConfig,
            boolean localConfigPresent,
            boolean localAnalysisSelected,
            boolean environmentConfigured) {
        this.profiles = List.copyOf(profiles);
        this.activeId = activeId == null ? "" : activeId.trim();
        this.effectiveConfig = effectiveConfig;
        this.localConfigPresent = localConfigPresent;
        this.localAnalysisSelected = localAnalysisSelected;
        this.environmentConfigured = environmentConfigured;
    }

    public List<ModelConfigProfile> getProfiles() {
        return profiles;
    }

    public String getActiveId() {
        return activeId;
    }

    public ModelServiceConfig getEffectiveConfig() {
        return effectiveConfig;
    }

    public boolean isLocalConfigPresent() {
        return localConfigPresent;
    }

    public boolean isLocalAnalysisSelected() {
        return localAnalysisSelected;
    }

    public boolean isEnvironmentConfigured() {
        return environmentConfigured;
    }
}
