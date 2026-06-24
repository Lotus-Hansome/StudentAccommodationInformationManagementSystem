package com.dormitory;

public enum RepairStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    DONE("已完成"),
    REJECTED("已驳回"),
    CANCELED("已撤回");

    private final String displayName;

    RepairStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RepairStatus fromName(String name) {
        for (RepairStatus status : values()) {
            if (status.name().equalsIgnoreCase(name) || status.displayName.equals(name)) {
                return status;
            }
        }
        return PENDING;
    }
}
