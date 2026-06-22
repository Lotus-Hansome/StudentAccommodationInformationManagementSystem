package com.dormitory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public class DormStatistics {
    private final String scopeType;
    private final String scopeValue;
    private final int totalStudents;
    private final int roomCount;
    private final int totalCapacity;
    private final int vacantBeds;
    private final Map<String, Integer> departmentCounts;

    public DormStatistics(
            String scopeType,
            String scopeValue,
            int totalStudents,
            int roomCount,
            int totalCapacity,
            int vacantBeds,
            Map<String, Integer> departmentCounts) {
        this.scopeType = scopeType;
        this.scopeValue = scopeValue;
        this.totalStudents = totalStudents;
        this.roomCount = roomCount;
        this.totalCapacity = totalCapacity;
        this.vacantBeds = vacantBeds;
        this.departmentCounts = Collections.unmodifiableMap(new LinkedHashMap<>(departmentCounts));
    }

    public String getScopeType() {
        return scopeType;
    }

    public String getScopeValue() {
        return scopeValue;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public int getRoomCount() {
        return roomCount;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getVacantBeds() {
        return vacantBeds;
    }

    public Map<String, Integer> getDepartmentCounts() {
        return departmentCounts;
    }

    public double getOccupancyRate() {
        if (totalCapacity == 0) {
            return 0;
        }
        return totalStudents * 100.0 / totalCapacity;
    }

    public String toPromptText() {
        StringJoiner departmentText = new StringJoiner("; ");
        for (Map.Entry<String, Integer> entry : departmentCounts.entrySet()) {
            double ratio = totalStudents == 0 ? 0 : entry.getValue() * 100.0 / totalStudents;
            departmentText.add(entry.getKey() + "=" + entry.getValue() + "人/" + String.format("%.1f%%", ratio));
        }
        return "统计范围：" + scopeType + " " + scopeValue
                + "\n宿舍数量：" + roomCount
                + "\n总床位：" + totalCapacity
                + "\n已入住人数：" + totalStudents
                + "\n空余床位：" + vacantBeds
                + "\n入住率：" + String.format("%.1f%%", getOccupancyRate())
                + "\n各系入住人数比例：" + (departmentText.length() == 0 ? "暂无数据" : departmentText);
    }
}
