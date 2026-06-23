package com.dormitory;

public class StudentSearchCriteria {
    private String mode = "all";
    private String studentId = "";
    private String dormNumber = "";
    private String buildingNumber = "";
    private String department = "";
    private String className = "";
    private String keyword = "";
    private String sort = "dorm";
    private int page = 1;
    private int pageSize = 10;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = normalize(mode);
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = normalize(studentId);
    }

    public String getDormNumber() {
        return dormNumber;
    }

    public void setDormNumber(String dormNumber) {
        this.dormNumber = normalize(dormNumber);
    }

    public String getBuildingNumber() {
        return buildingNumber;
    }

    public void setBuildingNumber(String buildingNumber) {
        this.buildingNumber = normalize(buildingNumber);
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = normalize(department);
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = normalize(className);
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = normalize(keyword);
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = normalize(sort);
    }

    public int getPage() {
        return Math.max(1, page);
    }

    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public int getPageSize() {
        return Math.min(Math.max(1, pageSize), 100);
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.min(Math.max(1, pageSize), 100);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
