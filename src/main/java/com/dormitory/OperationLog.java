package com.dormitory;

import java.time.LocalDateTime;

public class OperationLog {
    private final long id;
    private final String operator;
    private final String action;
    private final String targetType;
    private final String targetId;
    private final String detail;
    private final LocalDateTime createdAt;

    public OperationLog(long id, String operator, String action, String targetType, String targetId, String detail, LocalDateTime createdAt) {
        this.id = id;
        this.operator = normalize(operator);
        this.action = normalize(action);
        this.targetType = normalize(targetType);
        this.targetId = normalize(targetId);
        this.detail = normalize(detail);
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getOperator() {
        return operator;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
