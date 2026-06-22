package com.dormitory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class StudentDormService {
    public static final int DEFAULT_BEDS_PER_DORM = 6;

    private final StudentRepository repository;
    private final List<StudentDormRecord> records;

    public StudentDormService(StudentRepository repository) {
        this.repository = repository;
        try {
            this.records = new ArrayList<>(repository.load());
        } catch (IOException e) {
            throw new IllegalStateException("读取学生宿舍数据失败：" + e.getMessage(), e);
        }
    }

    public void add(StudentDormRecord record) {
        validateRequired(record);
        if (findByStudentId(record.getStudentId()).isPresent()) {
            throw new IllegalArgumentException("学号已存在，不能重复添加。");
        }
        if (!isBedAvailable(record.getDormNumber(), record.getBedNumber(), record.getStudentId())) {
            throw new IllegalArgumentException("目标宿舍床位已被占用。");
        }
        records.add(record);
        save();
    }

    public void update(StudentDormRecord record) {
        validateRequired(record);
        StudentDormRecord existing = findByStudentId(record.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("未找到该学生，无法修改。"));
        if (!isBedAvailable(record.getDormNumber(), record.getBedNumber(), record.getStudentId())) {
            throw new IllegalArgumentException("目标宿舍床位已被占用。");
        }
        existing.setName(record.getName());
        existing.setDepartment(record.getDepartment());
        existing.setClassName(record.getClassName());
        existing.setDormNumber(record.getDormNumber());
        existing.setDormPhone(record.getDormPhone());
        existing.setBedNumber(record.getBedNumber());
        save();
    }

    public boolean deleteByDormAndStudent(String dormNumber, String studentId) {
        boolean removed = records.removeIf(record ->
                record.getDormNumber().equalsIgnoreCase(dormNumber)
                        && record.getStudentId().equalsIgnoreCase(studentId));
        if (removed) {
            save();
        }
        return removed;
    }

    public Optional<StudentDormRecord> findByStudentId(String studentId) {
        return records.stream()
                .filter(record -> record.getStudentId().equalsIgnoreCase(studentId))
                .findFirst();
    }

    public List<StudentDormRecord> findByDormNumber(String dormNumber) {
        return records.stream()
                .filter(record -> record.getDormNumber().equalsIgnoreCase(dormNumber))
                .sorted(Comparator.comparing(StudentDormRecord::getBedNumber))
                .collect(Collectors.toList());
    }

    public List<StudentDormRecord> listAll() {
        return records.stream()
                .sorted(Comparator.comparing(StudentDormRecord::getDormNumber)
                        .thenComparing(StudentDormRecord::getBedNumber))
                .collect(Collectors.toList());
    }

    public List<StudentDormRecord> sortByDepartmentAndClass() {
        return records.stream()
                .sorted(Comparator.comparing(StudentDormRecord::getDepartment)
                        .thenComparing(StudentDormRecord::getClassName)
                        .thenComparing(StudentDormRecord::getStudentId))
                .collect(Collectors.toList());
    }

    public void updateDorm(String studentId, String targetDormNumber, String targetDormPhone, String targetBedNumber) {
        StudentDormRecord record = findByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("未找到该学生，无法更新宿舍。"));
        if (!isBedAvailable(targetDormNumber, targetBedNumber, studentId)) {
            throw new IllegalArgumentException("目标宿舍床位已被占用。");
        }
        record.setDormNumber(targetDormNumber);
        record.setDormPhone(targetDormPhone);
        record.setBedNumber(targetBedNumber);
        save();
    }

    public boolean isBedAvailable(String dormNumber, String bedNumber, String excludingStudentId) {
        return records.stream().noneMatch(record ->
                record.isSameBed(dormNumber, bedNumber)
                        && !record.getStudentId().equalsIgnoreCase(excludingStudentId == null ? "" : excludingStudentId));
    }

    public DormStatistics statisticsByDorm(String dormNumber) {
        List<StudentDormRecord> matched = findByDormNumber(dormNumber);
        int capacity = DEFAULT_BEDS_PER_DORM;
        return buildStatistics("宿舍", dormNumber, matched, capacity);
    }

    public DormStatistics statisticsByBuilding(String buildingNumber) {
        String trimmed = buildingNumber.trim();
        List<StudentDormRecord> matched = records.stream()
                .filter(record -> record.getBuildingNumber().equalsIgnoreCase(trimmed))
                .collect(Collectors.toList());
        Set<String> rooms = matched.stream()
                .map(StudentDormRecord::getDormNumber)
                .collect(Collectors.toCollection(TreeSet::new));
        int capacity = rooms.size() * DEFAULT_BEDS_PER_DORM;
        return buildStatistics("楼栋", trimmed, matched, capacity);
    }

    public DormStatistics statisticsAll() {
        Set<String> rooms = records.stream()
                .map(StudentDormRecord::getDormNumber)
                .collect(Collectors.toCollection(TreeSet::new));
        int capacity = rooms.size() * DEFAULT_BEDS_PER_DORM;
        return buildStatistics("全校", "全部宿舍", records, capacity);
    }

    private DormStatistics buildStatistics(String scopeType, String scopeValue, List<StudentDormRecord> matched, int capacity) {
        Map<String, Integer> departmentCounts = new LinkedHashMap<>();
        matched.stream()
                .collect(Collectors.groupingBy(StudentDormRecord::getDepartment, TreeMap::new, Collectors.counting()))
                .forEach((department, count) -> departmentCounts.put(department, count.intValue()));
        int roomCount = matched.stream()
                .map(StudentDormRecord::getDormNumber)
                .collect(Collectors.toSet())
                .size();
        int vacantBeds = Math.max(0, capacity - matched.size());
        return new DormStatistics(scopeType, scopeValue, matched.size(), roomCount, capacity, vacantBeds, departmentCounts);
    }

    private void validateRequired(StudentDormRecord record) {
        if (isBlank(record.getStudentId())
                || isBlank(record.getName())
                || isBlank(record.getDepartment())
                || isBlank(record.getClassName())
                || isBlank(record.getDormNumber())
                || isBlank(record.getDormPhone())
                || isBlank(record.getBedNumber())) {
            throw new IllegalArgumentException("学生宿舍信息字段不能为空。");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void save() {
        try {
            repository.save(records);
        } catch (IOException e) {
            throw new IllegalStateException("保存学生宿舍数据失败：" + e.getMessage(), e);
        }
    }
}
