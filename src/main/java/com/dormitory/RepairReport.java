package com.dormitory;

import java.time.LocalDateTime;

public class RepairReport {
    private final String id;
    private final String studentId;
    private final String dormNumber;
    private final String category;
    private final String description;
    private RepairStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime handledAt;
    private String adminComment;

    public RepairReport(
            String id,
            String studentId,
            String dormNumber,
            String category,
            String description,
            RepairStatus status,
            LocalDateTime createdAt,
            LocalDateTime handledAt,
            String adminComment) {
        this.id = id;
        this.studentId = studentId;
        this.dormNumber = dormNumber;
        this.category = category;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.handledAt = handledAt;
        this.adminComment = adminComment;
    }

    public String getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getDormNumber() {
        return dormNumber;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public RepairStatus getStatus() {
        return status;
    }

    public void setStatus(RepairStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public String getAdminComment() {
        return adminComment;
    }

    public void setAdminComment(String adminComment) {
        this.adminComment = adminComment;
    }
}
