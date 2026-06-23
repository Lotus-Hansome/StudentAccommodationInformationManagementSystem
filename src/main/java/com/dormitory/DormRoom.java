package com.dormitory;

public class DormRoom {
    private final String dormNumber;
    private final String buildingNumber;
    private final int floorNumber;
    private final String roomType;
    private final String genderType;
    private final int capacity;
    private final String phone;
    private final String status;

    public DormRoom(
            String dormNumber,
            String buildingNumber,
            int floorNumber,
            String roomType,
            String genderType,
            int capacity,
            String phone,
            String status) {
        this.dormNumber = normalize(dormNumber);
        this.buildingNumber = normalize(buildingNumber);
        this.floorNumber = floorNumber;
        this.roomType = normalize(roomType);
        this.genderType = normalize(genderType);
        this.capacity = capacity;
        this.phone = normalize(phone);
        this.status = normalize(status).isBlank() ? "ACTIVE" : normalize(status);
    }

    public String getDormNumber() {
        return dormNumber;
    }

    public String getBuildingNumber() {
        return buildingNumber;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public String getRoomType() {
        return roomType;
    }

    public String getGenderType() {
        return genderType;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getPhone() {
        return phone;
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
