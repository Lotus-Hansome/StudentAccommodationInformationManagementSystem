package com.dormitory;

public class DormBed {
    private final String dormNumber;
    private final String bedNumber;
    private final String status;

    public DormBed(String dormNumber, String bedNumber, String status) {
        this.dormNumber = normalize(dormNumber);
        this.bedNumber = normalize(bedNumber);
        this.status = normalize(status).isBlank() ? "ACTIVE" : normalize(status);
    }

    public String getDormNumber() {
        return dormNumber;
    }

    public String getBedNumber() {
        return bedNumber;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
