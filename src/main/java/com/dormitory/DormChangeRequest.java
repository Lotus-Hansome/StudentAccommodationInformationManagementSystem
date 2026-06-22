package com.dormitory;

import java.time.LocalDateTime;

public class DormChangeRequest {
    private String id;
    private String studentId;
    private String currentDormNumber;
    private String currentBedNumber;
    private String targetDormNumber;
    private String targetDormPhone;
    private String targetBedNumber;
    private String reason;
    private ChangeRequestStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
    private String adminComment;

    public DormChangeRequest(
            String id,
            String studentId,
            String currentDormNumber,
            String currentBedNumber,
            String targetDormNumber,
            String targetDormPhone,
            String targetBedNumber,
            String reason,
            ChangeRequestStatus status,
            LocalDateTime createdAt,
            LocalDateTime handledAt,
            String adminComment) {
        this.id = id;
        this.studentId = studentId;
        this.currentDormNumber = currentDormNumber;
        this.currentBedNumber = currentBedNumber;
        this.targetDormNumber = targetDormNumber;
        this.targetDormPhone = targetDormPhone;
        this.targetBedNumber = targetBedNumber;
        this.reason = reason;
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

    public String getCurrentDormNumber() {
        return currentDormNumber;
    }

    public String getCurrentBedNumber() {
        return currentBedNumber;
    }

    public String getTargetDormNumber() {
        return targetDormNumber;
    }

    public String getTargetDormPhone() {
        return targetDormPhone;
    }

    public String getTargetBedNumber() {
        return targetBedNumber;
    }

    public String getReason() {
        return reason;
    }

    public ChangeRequestStatus getStatus() {
        return status;
    }

    public void setStatus(ChangeRequestStatus status) {
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
