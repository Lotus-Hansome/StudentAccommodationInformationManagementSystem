package com.dormitory;

public enum ChangeRequestStatus {
    PENDING("待审核"),
    APPROVED("已同意"),
    REJECTED("已拒绝"),
    CANCELED("已撤回");

    private final String displayName;

    ChangeRequestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ChangeRequestStatus fromName(String name) {
        for (ChangeRequestStatus status : values()) {
            if (status.name().equalsIgnoreCase(name) || status.displayName.equals(name)) {
                return status;
            }
        }
        return PENDING;
    }
}
