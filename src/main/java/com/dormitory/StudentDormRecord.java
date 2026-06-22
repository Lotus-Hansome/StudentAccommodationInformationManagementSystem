package com.dormitory;

import java.util.Objects;

public class StudentDormRecord {
    private String studentId;
    private String name;
    private String department;
    private String className;
    private String dormNumber;
    private String dormPhone;
    private String bedNumber;

    public StudentDormRecord(
            String studentId,
            String name,
            String department,
            String className,
            String dormNumber,
            String dormPhone,
            String bedNumber) {
        this.studentId = studentId;
        this.name = name;
        this.department = department;
        this.className = className;
        this.dormNumber = dormNumber;
        this.dormPhone = dormPhone;
        this.bedNumber = bedNumber;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getDormNumber() {
        return dormNumber;
    }

    public void setDormNumber(String dormNumber) {
        this.dormNumber = dormNumber;
    }

    public String getDormPhone() {
        return dormPhone;
    }

    public void setDormPhone(String dormPhone) {
        this.dormPhone = dormPhone;
    }

    public String getBedNumber() {
        return bedNumber;
    }

    public void setBedNumber(String bedNumber) {
        this.bedNumber = bedNumber;
    }

    public boolean isSameBed(String dormNumber, String bedNumber) {
        return Objects.equals(this.dormNumber, dormNumber) && Objects.equals(this.bedNumber, bedNumber);
    }

    public String getBuildingNumber() {
        String value = dormNumber == null ? "" : dormNumber.trim();
        int hyphenIndex = value.indexOf('-');
        if (hyphenIndex > 0) {
            return value.substring(0, hyphenIndex);
        }
        int buildingIndex = value.indexOf("号楼");
        if (buildingIndex > 0) {
            return value.substring(0, buildingIndex);
        }
        int shortBuildingIndex = value.indexOf('楼');
        if (shortBuildingIndex > 0) {
            return value.substring(0, shortBuildingIndex);
        }
        return value;
    }
}
