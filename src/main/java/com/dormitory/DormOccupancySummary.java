package com.dormitory;

public class DormOccupancySummary {
    private final String scope;
    private final String buildingNumber;
    private final String dormNumber;
    private final int roomCount;
    private final int totalStudents;
    private final int totalCapacity;
    private final int vacantBeds;

    public DormOccupancySummary(
            String scope,
            String buildingNumber,
            String dormNumber,
            int roomCount,
            int totalStudents,
            int totalCapacity) {
        this.scope = scope;
        this.buildingNumber = buildingNumber;
        this.dormNumber = dormNumber;
        this.roomCount = roomCount;
        this.totalStudents = totalStudents;
        this.totalCapacity = totalCapacity;
        this.vacantBeds = Math.max(0, totalCapacity - totalStudents);
    }

    public String getScope() {
        return scope;
    }

    public String getBuildingNumber() {
        return buildingNumber;
    }

    public String getDormNumber() {
        return dormNumber;
    }

    public int getRoomCount() {
        return roomCount;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getVacantBeds() {
        return vacantBeds;
    }

    public double getOccupancyRate() {
        if (totalCapacity == 0) {
            return 0;
        }
        return totalStudents * 100.0 / totalCapacity;
    }
}
