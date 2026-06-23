package com.dormitory;

public class Building {
    private final String buildingNumber;
    private final String buildingName;
    private final String genderType;
    private final int totalFloors;
    private final String status;

    public Building(String buildingNumber, String buildingName, String genderType, int totalFloors, String status) {
        this.buildingNumber = normalize(buildingNumber);
        this.buildingName = normalize(buildingName);
        this.genderType = normalize(genderType);
        this.totalFloors = totalFloors;
        this.status = normalize(status).isBlank() ? "ACTIVE" : normalize(status);
    }

    public String getBuildingNumber() {
        return buildingNumber;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public String getGenderType() {
        return genderType;
    }

    public int getTotalFloors() {
        return totalFloors;
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
