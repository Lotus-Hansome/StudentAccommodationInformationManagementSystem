package com.dormitory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class StudentDormService {
    public static final int DEFAULT_BEDS_PER_DORM = 4;

    private final StudentRepository repository;
    private final DormInfrastructureService infrastructureService;
    private final List<StudentDormRecord> records;

    public StudentDormService(StudentRepository repository) {
        this(repository, null);
    }

    public StudentDormService(StudentRepository repository, DormInfrastructureService infrastructureService) {
        this.repository = repository;
        this.infrastructureService = infrastructureService;
        try {
            this.records = new ArrayList<>(repository.load());
        } catch (IOException e) {
            throw new IllegalStateException("读取学生宿舍数据失败：" + e.getMessage(), e);
        }
    }

    public synchronized void add(StudentDormRecord record) {
        normalize(record);
        validateRequired(record);
        if (findByStudentId(record.getStudentId()).isPresent()) {
            throw new IllegalArgumentException("学号已存在，不能重复添加。");
        }
        if (!isBedAvailable(record.getDormNumber(), record.getBedNumber(), record.getStudentId())) {
            throw new IllegalArgumentException("目标宿舍床位已被占用。");
        }
        record.setDormPhone(resolveDormPhone(record.getDormNumber(), record.getDormPhone()));
        try {
            repository.insert(record);
            records.add(record);
        } catch (IOException e) {
            throw new IllegalStateException("保存学生宿舍数据失败：" + e.getMessage(), e);
        }
    }

    public synchronized void update(StudentDormRecord record) {
        normalize(record);
        validateRequired(record);
        StudentDormRecord existing = findByStudentId(record.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("未找到该学生，无法修改。"));
        if (!isBedAvailable(record.getDormNumber(), record.getBedNumber(), record.getStudentId())) {
            throw new IllegalArgumentException("目标宿舍床位已被占用。");
        }
        record.setDormPhone(resolveDormPhone(record.getDormNumber(), record.getDormPhone()));
        try {
            repository.update(record);
            replaceRecord(existing, record);
        } catch (IOException e) {
            throw new IllegalStateException("保存学生宿舍数据失败：" + e.getMessage(), e);
        }
    }

    public synchronized boolean deleteByDormAndStudent(String dormNumber, String studentId) {
        String normalizedDormNumber = normalizeText(dormNumber);
        String normalizedStudentId = normalizeText(studentId);
        try {
            boolean removed = repository.deleteByDormAndStudent(normalizedDormNumber, normalizedStudentId);
            if (removed) {
                records.removeIf(record ->
                        record.getDormNumber().equalsIgnoreCase(normalizedDormNumber)
                                && record.getStudentId().equalsIgnoreCase(normalizedStudentId));
            }
            return removed;
        } catch (IOException e) {
            throw new IllegalStateException("删除学生宿舍数据失败：" + e.getMessage(), e);
        }
    }

    public synchronized Optional<StudentDormRecord> findByStudentId(String studentId) {
        String normalizedStudentId = normalizeText(studentId);
        return records.stream()
                .filter(record -> record.getStudentId().equalsIgnoreCase(normalizedStudentId))
                .findFirst();
    }

    public synchronized List<StudentDormRecord> findByDormNumber(String dormNumber) {
        String normalizedDormNumber = normalizeText(dormNumber);
        return records.stream()
                .filter(record -> record.getDormNumber().equalsIgnoreCase(normalizedDormNumber))
                .sorted(Comparator.comparing(StudentDormRecord::getBedNumber))
                .collect(Collectors.toList());
    }

    public synchronized List<StudentDormRecord> findByDepartmentAndClass(String department, String className) {
        String normalizedDepartment = normalizeText(department);
        String normalizedClassName = normalizeText(className);
        return records.stream()
                .filter(record -> matchesText(record.getDepartment(), normalizedDepartment))
                .filter(record -> matchesText(record.getClassName(), normalizedClassName))
                .sorted(Comparator.comparing(StudentDormRecord::getDepartment)
                        .thenComparing(StudentDormRecord::getClassName)
                        .thenComparing(StudentDormRecord::getStudentId))
                .collect(Collectors.toList());
    }

    public synchronized List<StudentDormRecord> listAll() {
        return records.stream()
                .sorted(Comparator.comparing(StudentDormRecord::getDormNumber)
                        .thenComparing(StudentDormRecord::getBedNumber))
                .collect(Collectors.toList());
    }

    public synchronized List<StudentDormRecord> sortByDepartmentAndClass() {
        return records.stream()
                .sorted(Comparator.comparing(StudentDormRecord::getDepartment)
                        .thenComparing(StudentDormRecord::getClassName)
                        .thenComparing(StudentDormRecord::getStudentId))
                .collect(Collectors.toList());
    }

    public synchronized PageResult<StudentDormRecord> search(StudentSearchCriteria criteria) {
        if (repository instanceof MysqlStudentRepository mysqlRepository) {
            try {
                return mysqlRepository.search(criteria);
            } catch (IOException e) {
                throw new IllegalStateException("分页查询学生住宿数据失败：" + e.getMessage(), e);
            }
        }
        List<StudentDormRecord> filtered = filterRecords(criteria);
        int total = filtered.size();
        int page = criteria.getPage();
        int pageSize = criteria.getPageSize();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return new PageResult<>(filtered.subList(from, to), total, page, pageSize);
    }

    public synchronized void updateDorm(String studentId, String targetDormNumber, String targetDormPhone, String targetBedNumber) {
        String normalizedStudentId = normalizeText(studentId);
        String normalizedDormNumber = normalizeText(targetDormNumber);
        String normalizedDormPhone = normalizeText(targetDormPhone);
        String normalizedBedNumber = normalizeText(targetBedNumber);
        StudentDormRecord record = findByStudentId(normalizedStudentId)
                .orElseThrow(() -> new IllegalArgumentException("未找到该学生，无法更新宿舍。"));
        if (isBlank(normalizedDormNumber) || isBlank(normalizedBedNumber)) {
            throw new IllegalArgumentException("目标宿舍和床位不能为空。");
        }
        if (!isBedAvailable(normalizedDormNumber, normalizedBedNumber, normalizedStudentId)) {
            throw new IllegalArgumentException("目标宿舍床位已被占用。");
        }
        StudentDormRecord updated = new StudentDormRecord(
                record.getStudentId(),
                record.getName(),
                record.getDepartment(),
                record.getClassName(),
                normalizedDormNumber,
                resolveDormPhone(normalizedDormNumber, normalizedDormPhone),
                normalizedBedNumber);
        try {
            repository.update(updated);
            replaceRecord(record, updated);
        } catch (IOException e) {
            throw new IllegalStateException("保存学生宿舍数据失败：" + e.getMessage(), e);
        }
    }

    public synchronized boolean isBedAvailable(String dormNumber, String bedNumber, String excludingStudentId) {
        String normalizedDormNumber = normalizeText(dormNumber);
        String normalizedBedNumber = normalizeText(bedNumber);
        String normalizedExcludingStudentId = normalizeText(excludingStudentId);
        validateBedExists(normalizedDormNumber, normalizedBedNumber);
        return records.stream().noneMatch(record ->
                record.isSameBed(normalizedDormNumber, normalizedBedNumber)
                        && !record.getStudentId().equalsIgnoreCase(normalizedExcludingStudentId));
    }

    public synchronized DormStatistics statisticsByDorm(String dormNumber) {
        List<StudentDormRecord> matched = findByDormNumber(dormNumber);
        boolean hasInfrastructure = infrastructureService != null && infrastructureService.hasInfrastructureData();
        Optional<DormRoom> room = hasInfrastructure ? infrastructureService.findRoom(dormNumber) : Optional.empty();
        int capacity = hasInfrastructure
                ? room.filter(DormRoom::isActive).map(DormRoom::getCapacity).orElse(0)
                : (matched.isEmpty() ? 0 : DEFAULT_BEDS_PER_DORM);
        int roomCount = hasInfrastructure
                ? (room.filter(DormRoom::isActive).isPresent() ? 1 : 0)
                : (matched.isEmpty() ? 0 : 1);
        return buildStatistics("宿舍", dormNumber, matched, roomCount, capacity);
    }

    public synchronized DormStatistics statisticsByBuilding(String buildingNumber) {
        String trimmed = infrastructureService == null
                ? buildingNumber.trim()
                : infrastructureService.normalizeBuildingNumber(buildingNumber);
        List<StudentDormRecord> matched = records.stream()
                .filter(record -> record.getBuildingNumber().equalsIgnoreCase(trimmed))
                .collect(Collectors.toList());
        Set<String> rooms = infrastructureService == null || !infrastructureService.hasInfrastructureData()
                ? matched.stream().map(StudentDormRecord::getDormNumber).collect(Collectors.toCollection(TreeSet::new))
                : infrastructureService.listRooms(trimmed, "").stream()
                        .filter(DormRoom::isActive)
                        .map(DormRoom::getDormNumber)
                        .collect(Collectors.toCollection(TreeSet::new));
        int capacity = infrastructureService == null || !infrastructureService.hasInfrastructureData()
                ? rooms.size() * DEFAULT_BEDS_PER_DORM
                : infrastructureService.buildingCapacity(trimmed);
        return buildStatistics("楼栋", trimmed, matched, rooms.size(), capacity);
    }

    public synchronized DormStatistics statisticsAll() {
        Set<String> rooms = infrastructureService == null || !infrastructureService.hasInfrastructureData()
                ? records.stream().map(StudentDormRecord::getDormNumber).collect(Collectors.toCollection(TreeSet::new))
                : infrastructureService.activeDormNumbers();
        int capacity = infrastructureService == null || !infrastructureService.hasInfrastructureData()
                ? rooms.size() * DEFAULT_BEDS_PER_DORM
                : infrastructureService.totalCapacity();
        return buildStatistics("全校", "全部宿舍", records, rooms.size(), capacity);
    }

    public synchronized List<DormOccupancySummary> buildingOccupancySummaries() {
        return buildingOccupancySummaries("");
    }

    public synchronized List<DormOccupancySummary> buildingOccupancySummaries(String buildingNumber) {
        String normalizedBuildingNumber = normalizeBuildingNumber(buildingNumber);
        if (infrastructureService != null && infrastructureService.hasInfrastructureData()) {
            Map<String, List<DormRoom>> roomsByBuilding = infrastructureService.activeRoomsByBuilding();
            return roomsByBuilding.entrySet().stream()
                    .filter(entry -> isBlank(normalizedBuildingNumber) || entry.getKey().equalsIgnoreCase(normalizedBuildingNumber))
                    .sorted(Map.Entry.comparingByKey(this::compareNumbersAsText))
                    .map(entry -> {
                        Set<String> dormNumbers = entry.getValue().stream()
                                .map(DormRoom::getDormNumber)
                                .collect(Collectors.toCollection(TreeSet::new));
                        int students = (int) records.stream()
                                .filter(record -> dormNumbers.contains(record.getDormNumber()))
                                .count();
                        int capacity = entry.getValue().stream().mapToInt(DormRoom::getCapacity).sum();
                        return new DormOccupancySummary(
                                entry.getKey() + "号楼",
                                entry.getKey(),
                                "",
                                entry.getValue().size(),
                                students,
                                capacity);
                    })
                    .collect(Collectors.toList());
        }
        Map<String, List<StudentDormRecord>> byBuilding = records.stream()
                .filter(record -> isBlank(normalizedBuildingNumber) || record.getBuildingNumber().equalsIgnoreCase(normalizedBuildingNumber))
                .collect(Collectors.groupingBy(StudentDormRecord::getBuildingNumber, TreeMap::new, Collectors.toList()));
        return byBuilding.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(this::compareNumbersAsText))
                .map(entry -> {
                    Set<String> rooms = entry.getValue().stream()
                            .map(StudentDormRecord::getDormNumber)
                            .collect(Collectors.toCollection(TreeSet::new));
                    return new DormOccupancySummary(
                            entry.getKey() + "号楼",
                            entry.getKey(),
                            "",
                            rooms.size(),
                            entry.getValue().size(),
                            rooms.size() * DEFAULT_BEDS_PER_DORM);
                })
                .collect(Collectors.toList());
    }

    public synchronized List<DormOccupancySummary> dormOccupancySummaries() {
        return dormOccupancySummaries("");
    }

    public synchronized List<DormOccupancySummary> dormOccupancySummaries(String dormNumber) {
        String normalizedDormNumber = normalizeText(dormNumber);
        if (infrastructureService != null && infrastructureService.hasInfrastructureData()) {
            return infrastructureService.listRooms("", normalizedDormNumber).stream()
                    .filter(DormRoom::isActive)
                    .map(room -> {
                        int students = (int) records.stream()
                                .filter(record -> record.getDormNumber().equalsIgnoreCase(room.getDormNumber()))
                                .count();
                        return new DormOccupancySummary(
                                room.getDormNumber(),
                                room.getBuildingNumber(),
                                room.getDormNumber(),
                                1,
                                students,
                                room.getCapacity());
                    })
                    .collect(Collectors.toList());
        }
        Map<String, List<StudentDormRecord>> byDorm = records.stream()
                .filter(record -> isBlank(normalizedDormNumber)
                        || record.getDormNumber().equalsIgnoreCase(normalizedDormNumber)
                        || record.getDormNumber().toLowerCase(Locale.ROOT).contains(normalizedDormNumber.toLowerCase(Locale.ROOT)))
                .collect(Collectors.groupingBy(StudentDormRecord::getDormNumber, TreeMap::new, Collectors.toList()));
        return byDorm.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(this::compareDormNumbers))
                .map(entry -> {
                    String buildingNumber = entry.getValue().isEmpty() ? "" : entry.getValue().get(0).getBuildingNumber();
                    return new DormOccupancySummary(
                            entry.getKey(),
                            buildingNumber,
                            entry.getKey(),
                            1,
                            entry.getValue().size(),
                            DEFAULT_BEDS_PER_DORM);
                })
                .collect(Collectors.toList());
    }

    public synchronized void validateRoomChange(DormRoom room) {
        List<StudentDormRecord> occupants = findByDormNumber(room.getDormNumber());
        if (!room.isActive() && !occupants.isEmpty()) {
            throw new IllegalArgumentException("该宿舍仍有学生入住，不能停用。");
        }
        for (StudentDormRecord occupant : occupants) {
            int bedNumber;
            try {
                bedNumber = Integer.parseInt(occupant.getBedNumber());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("宿舍存在非数字床位，不能直接修改容量。");
            }
            if (bedNumber < 1 || bedNumber > room.getCapacity()) {
                throw new IllegalArgumentException(
                        "学生 " + occupant.getStudentId() + " 当前使用 " + occupant.getBedNumber()
                                + " 号床，宿舍容量不能缩减为 " + room.getCapacity() + "。");
            }
        }
    }

    public synchronized void synchronizeRoomPhone(DormRoom room) {
        List<StudentDormRecord> occupants = findByDormNumber(room.getDormNumber());
        if (occupants.isEmpty()) {
            return;
        }
        for (StudentDormRecord occupant : occupants) {
            StudentDormRecord updated = new StudentDormRecord(
                    occupant.getStudentId(),
                    occupant.getName(),
                    occupant.getDepartment(),
                    occupant.getClassName(),
                    occupant.getDormNumber(),
                    room.getPhone(),
                    occupant.getBedNumber());
            try {
                repository.update(updated);
                replaceRecord(occupant, updated);
            } catch (IOException e) {
                throw new IllegalStateException("同步宿舍电话失败：" + e.getMessage(), e);
            }
        }
    }

    public synchronized String dormPhoneFor(String dormNumber, String fallbackPhone) {
        return resolveDormPhone(normalizeText(dormNumber), normalizeText(fallbackPhone));
    }

    private DormStatistics buildStatistics(
            String scopeType,
            String scopeValue,
            List<StudentDormRecord> matched,
            int roomCount,
            int capacity) {
        Map<String, Integer> departmentCounts = new LinkedHashMap<>();
        matched.stream()
                .collect(Collectors.groupingBy(StudentDormRecord::getDepartment, TreeMap::new, Collectors.counting()))
                .forEach((department, count) -> departmentCounts.put(department, count.intValue()));
        int vacantBeds = Math.max(0, capacity - matched.size());
        return new DormStatistics(scopeType, scopeValue, matched.size(), roomCount, capacity, vacantBeds, departmentCounts);
    }

    private List<StudentDormRecord> filterRecords(StudentSearchCriteria criteria) {
        String buildingNumber = normalizeBuildingNumber(criteria.getBuildingNumber());
        String keyword = normalizeText(criteria.getKeyword()).toLowerCase(Locale.ROOT);
        Comparator<StudentDormRecord> comparator = "department".equalsIgnoreCase(criteria.getSort())
                || "departmentClass".equalsIgnoreCase(criteria.getSort())
                ? Comparator.comparing(StudentDormRecord::getDepartment)
                        .thenComparing(StudentDormRecord::getClassName)
                        .thenComparing(StudentDormRecord::getStudentId)
                : Comparator.comparing(StudentDormRecord::getDormNumber)
                        .thenComparing(StudentDormRecord::getBedNumber);
        return records.stream()
                .filter(record -> isBlank(criteria.getStudentId()) || record.getStudentId().equalsIgnoreCase(criteria.getStudentId()))
                .filter(record -> isBlank(criteria.getDormNumber()) || record.getDormNumber().equalsIgnoreCase(criteria.getDormNumber()))
                .filter(record -> isBlank(buildingNumber) || record.getBuildingNumber().equalsIgnoreCase(buildingNumber))
                .filter(record -> matchesText(record.getDepartment(), criteria.getDepartment()))
                .filter(record -> matchesText(record.getClassName(), criteria.getClassName()))
                .filter(record -> isBlank(keyword)
                        || record.getStudentId().toLowerCase(Locale.ROOT).contains(keyword)
                        || record.getName().toLowerCase(Locale.ROOT).contains(keyword)
                        || record.getDepartment().toLowerCase(Locale.ROOT).contains(keyword)
                        || record.getClassName().toLowerCase(Locale.ROOT).contains(keyword)
                        || record.getDormNumber().toLowerCase(Locale.ROOT).contains(keyword))
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private void validateBedExists(String dormNumber, String bedNumber) {
        if (infrastructureService == null || !infrastructureService.hasInfrastructureData()) {
            return;
        }
        if (!infrastructureService.isRoomActive(dormNumber)) {
            throw new IllegalArgumentException("目标宿舍不存在或已停用。");
        }
        if (!infrastructureService.isBedActive(dormNumber, bedNumber)) {
            throw new IllegalArgumentException("目标床位不存在或已停用。");
        }
    }

    private String resolveDormPhone(String dormNumber, String inputPhone) {
        if (infrastructureService != null && infrastructureService.hasInfrastructureData()) {
            String configuredPhone = infrastructureService.roomPhone(dormNumber);
            if (!isBlank(configuredPhone)) {
                return configuredPhone;
            }
        }
        return normalizeText(inputPhone);
    }

    private void validateRequired(StudentDormRecord record) {
        if (isBlank(record.getStudentId())
                || isBlank(record.getName())
                || isBlank(record.getDepartment())
                || isBlank(record.getClassName())
                || isBlank(record.getDormNumber())
                || isBlank(record.getBedNumber())) {
            throw new IllegalArgumentException("学生宿舍信息字段不能为空。");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean matchesText(String actualValue, String queryValue) {
        if (isBlank(queryValue)) {
            return true;
        }
        String actual = normalizeText(actualValue).toLowerCase(Locale.ROOT);
        String query = normalizeText(queryValue).toLowerCase(Locale.ROOT);
        return actual.contains(query);
    }

    private String normalizeBuildingNumber(String value) {
        if (infrastructureService != null) {
            return infrastructureService.normalizeBuildingNumber(value);
        }
        String normalized = normalizeText(value);
        if (normalized.endsWith("号楼")) {
            return normalized.substring(0, normalized.length() - 2).trim();
        }
        if (normalized.endsWith("楼")) {
            return normalized.substring(0, normalized.length() - 1).trim();
        }
        int digitEnd = 0;
        while (digitEnd < normalized.length() && Character.isDigit(normalized.charAt(digitEnd))) {
            digitEnd++;
        }
        if (digitEnd > 0) {
            return normalized.substring(0, digitEnd);
        }
        return normalized;
    }

    private int compareDormNumbers(String first, String second) {
        String firstBuilding = buildingPart(first);
        String secondBuilding = buildingPart(second);
        int buildingCompare = compareNumbersAsText(firstBuilding, secondBuilding);
        if (buildingCompare != 0) {
            return buildingCompare;
        }
        return compareNumbersAsText(roomPart(first), roomPart(second));
    }

    private String buildingPart(String dormNumber) {
        int index = normalizeText(dormNumber).indexOf('-');
        return index > 0 ? dormNumber.substring(0, index) : dormNumber;
    }

    private String roomPart(String dormNumber) {
        int index = normalizeText(dormNumber).indexOf('-');
        return index >= 0 && index + 1 < dormNumber.length() ? dormNumber.substring(index + 1) : "";
    }

    private int compareNumbersAsText(String first, String second) {
        try {
            return Integer.compare(Integer.parseInt(first), Integer.parseInt(second));
        } catch (NumberFormatException e) {
            return normalizeText(first).compareToIgnoreCase(normalizeText(second));
        }
    }

    private void normalize(StudentDormRecord record) {
        record.setStudentId(normalizeText(record.getStudentId()));
        record.setName(normalizeText(record.getName()));
        record.setDepartment(normalizeText(record.getDepartment()));
        record.setClassName(normalizeText(record.getClassName()));
        record.setDormNumber(normalizeText(record.getDormNumber()));
        record.setDormPhone(normalizeText(record.getDormPhone()));
        record.setBedNumber(normalizeText(record.getBedNumber()));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void replaceRecord(StudentDormRecord existing, StudentDormRecord replacement) {
        int index = records.indexOf(existing);
        if (index >= 0) {
            records.set(index, replacement);
        }
    }
}
