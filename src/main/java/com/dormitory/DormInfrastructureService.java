package com.dormitory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DormInfrastructureService {
    private final DormInfrastructureRepository repository;
    private final List<Building> buildings;
    private final List<DormRoom> rooms;
    private final List<DormBed> beds;

    public DormInfrastructureService(DormInfrastructureRepository repository) {
        this.repository = repository;
        try {
            this.buildings = new ArrayList<>(repository.loadBuildings());
            this.rooms = new ArrayList<>(repository.loadRooms());
            this.beds = new ArrayList<>(repository.loadBeds());
        } catch (IOException e) {
            throw new IllegalStateException("读取楼栋宿舍基础数据失败：" + e.getMessage(), e);
        }
    }

    public synchronized List<Building> listBuildings(String keyword) {
        String query = normalizeText(keyword).toLowerCase(Locale.ROOT);
        return buildings.stream()
                .filter(building -> query.isBlank()
                        || building.getBuildingNumber().toLowerCase(Locale.ROOT).contains(query)
                        || building.getBuildingName().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(Building::getBuildingNumber, this::compareNumbersAsText))
                .collect(Collectors.toList());
    }

    public synchronized List<DormRoom> listRooms(String buildingNumber, String keyword) {
        String building = normalizeBuildingNumber(buildingNumber);
        String query = normalizeText(keyword).toLowerCase(Locale.ROOT);
        return rooms.stream()
                .filter(room -> building.isBlank() || room.getBuildingNumber().equalsIgnoreCase(building))
                .filter(room -> query.isBlank()
                        || room.getDormNumber().toLowerCase(Locale.ROOT).contains(query)
                        || room.getRoomType().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(DormRoom::getDormNumber, this::compareDormNumbers))
                .collect(Collectors.toList());
    }

    public synchronized Optional<Building> findBuilding(String buildingNumber) {
        String normalized = normalizeBuildingNumber(buildingNumber);
        return buildings.stream()
                .filter(building -> building.getBuildingNumber().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public synchronized Optional<DormRoom> findRoom(String dormNumber) {
        String normalized = normalizeText(dormNumber);
        return rooms.stream()
                .filter(room -> room.getDormNumber().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public synchronized boolean hasInfrastructureData() {
        return !buildings.isEmpty() || !rooms.isEmpty();
    }

    public synchronized boolean isRoomActive(String dormNumber) {
        return findRoom(dormNumber).map(DormRoom::isActive).orElse(false);
    }

    public synchronized boolean isBedActive(String dormNumber, String bedNumber) {
        String normalizedDorm = normalizeText(dormNumber);
        String normalizedBed = normalizeText(bedNumber);
        return beds.stream()
                .anyMatch(bed -> bed.getDormNumber().equalsIgnoreCase(normalizedDorm)
                        && bed.getBedNumber().equalsIgnoreCase(normalizedBed)
                        && bed.isActive());
    }

    public synchronized List<String> activeBedNumbers(String dormNumber) {
        String normalizedDorm = normalizeText(dormNumber);
        return beds.stream()
                .filter(DormBed::isActive)
                .filter(bed -> bed.getDormNumber().equalsIgnoreCase(normalizedDorm))
                .map(DormBed::getBedNumber)
                .sorted(this::compareNumbersAsText)
                .collect(Collectors.toList());
    }

    public synchronized int roomCapacity(String dormNumber) {
        return findRoom(dormNumber).map(DormRoom::getCapacity).orElse(StudentDormService.DEFAULT_BEDS_PER_DORM);
    }

    public synchronized String roomPhone(String dormNumber) {
        return findRoom(dormNumber).map(DormRoom::getPhone).orElse("");
    }

    public synchronized int buildingCapacity(String buildingNumber) {
        String normalized = normalizeBuildingNumber(buildingNumber);
        return rooms.stream()
                .filter(DormRoom::isActive)
                .filter(room -> room.getBuildingNumber().equalsIgnoreCase(normalized))
                .mapToInt(DormRoom::getCapacity)
                .sum();
    }

    public synchronized int totalCapacity() {
        return rooms.stream()
                .filter(DormRoom::isActive)
                .mapToInt(DormRoom::getCapacity)
                .sum();
    }

    public synchronized Set<String> activeDormNumbers() {
        return rooms.stream()
                .filter(DormRoom::isActive)
                .map(DormRoom::getDormNumber)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public synchronized Map<String, List<DormRoom>> activeRoomsByBuilding() {
        return rooms.stream()
                .filter(DormRoom::isActive)
                .collect(Collectors.groupingBy(DormRoom::getBuildingNumber));
    }

    public synchronized void saveBuilding(Building building) {
        validateBuilding(building);
        try {
            repository.upsertBuilding(building);
            replaceBuilding(building);
        } catch (IOException e) {
            throw new IllegalStateException("保存楼栋失败：" + e.getMessage(), e);
        }
    }

    public synchronized void saveRoom(DormRoom room) {
        validateRoom(room);
        try {
            repository.upsertRoom(room);
            replaceRoom(room);
            replaceBeds(room);
        } catch (IOException e) {
            throw new IllegalStateException("保存宿舍失败：" + e.getMessage(), e);
        }
    }

    private void validateBuilding(Building building) {
        if (building.getBuildingNumber().isBlank() || building.getBuildingName().isBlank()) {
            throw new IllegalArgumentException("楼栋号和楼栋名称不能为空。");
        }
        validateGenderType(building.getGenderType());
        validateStatus(building.getStatus());
        if (building.getTotalFloors() <= 0 || building.getTotalFloors() > 100) {
            throw new IllegalArgumentException("楼层数必须在 1 到 100 之间。");
        }
        boolean hasRoomAboveLimit = rooms.stream()
                .anyMatch(room -> room.getBuildingNumber().equalsIgnoreCase(building.getBuildingNumber())
                        && room.getFloorNumber() > building.getTotalFloors());
        if (hasRoomAboveLimit) {
            throw new IllegalArgumentException("楼栋中存在高于新楼层数的宿舍，不能保存。");
        }
        boolean hasActiveRooms = rooms.stream()
                .anyMatch(room -> room.getBuildingNumber().equalsIgnoreCase(building.getBuildingNumber())
                        && room.isActive());
        if (!building.isActive() && hasActiveRooms) {
            throw new IllegalArgumentException("楼栋中仍有启用宿舍，请先停用相关宿舍。");
        }
        boolean hasGenderConflict = rooms.stream()
                .filter(room -> room.getBuildingNumber().equalsIgnoreCase(building.getBuildingNumber()))
                .anyMatch(room -> !isGenderCompatible(building.getGenderType(), room.getGenderType()));
        if (hasGenderConflict) {
            throw new IllegalArgumentException("楼栋内已有宿舍类型与新的楼栋类型不一致。");
        }
    }

    private void validateRoom(DormRoom room) {
        if (room.getDormNumber().isBlank() || room.getBuildingNumber().isBlank()) {
            throw new IllegalArgumentException("宿舍号和楼栋号不能为空。");
        }
        Optional<Building> building = findBuilding(room.getBuildingNumber());
        if (building.isEmpty()) {
            throw new IllegalArgumentException("楼栋不存在，不能添加宿舍。");
        }
        if (room.isActive() && !building.get().isActive()) {
            throw new IllegalArgumentException("所属楼栋已停用，不能启用该宿舍。");
        }
        if (!room.getDormNumber().toLowerCase(Locale.ROOT)
                .startsWith(room.getBuildingNumber().toLowerCase(Locale.ROOT) + "-")) {
            throw new IllegalArgumentException("宿舍号必须以所属楼栋号加连字符开头。");
        }
        if (room.getRoomType().isBlank()) {
            throw new IllegalArgumentException("宿舍类型不能为空。");
        }
        if (room.getPhone().isBlank()) {
            throw new IllegalArgumentException("宿舍电话不能为空。");
        }
        validateGenderType(room.getGenderType());
        validateStatus(room.getStatus());
        if (!isGenderCompatible(building.get().getGenderType(), room.getGenderType())) {
            throw new IllegalArgumentException("宿舍类型必须与所属楼栋类型一致。");
        }
        if (room.getCapacity() <= 0 || room.getCapacity() > 8) {
            throw new IllegalArgumentException("宿舍容量必须在 1 到 8 之间。");
        }
        if (room.getFloorNumber() <= 0) {
            throw new IllegalArgumentException("楼层必须大于 0。");
        }
        if (room.getFloorNumber() > building.get().getTotalFloors()) {
            throw new IllegalArgumentException("宿舍楼层不能超过楼栋总楼层数。");
        }
    }

    private void validateGenderType(String genderType) {
        if (!"MALE".equalsIgnoreCase(genderType)
                && !"FEMALE".equalsIgnoreCase(genderType)
                && !"MIXED".equalsIgnoreCase(genderType)) {
            throw new IllegalArgumentException("住宿类型只能是男生、女生或混合。");
        }
    }

    private void validateStatus(String status) {
        if (!"ACTIVE".equalsIgnoreCase(status) && !"DISABLED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("状态只能是启用或停用。");
        }
    }

    private boolean isGenderCompatible(String buildingGender, String roomGender) {
        return "MIXED".equalsIgnoreCase(buildingGender)
                || buildingGender.equalsIgnoreCase(roomGender);
    }

    private void replaceBuilding(Building building) {
        buildings.removeIf(item -> item.getBuildingNumber().equalsIgnoreCase(building.getBuildingNumber()));
        buildings.add(building);
    }

    private void replaceRoom(DormRoom room) {
        rooms.removeIf(item -> item.getDormNumber().equalsIgnoreCase(room.getDormNumber()));
        rooms.add(room);
    }

    private void replaceBeds(DormRoom room) {
        beds.removeIf(item -> item.getDormNumber().equalsIgnoreCase(room.getDormNumber()));
        for (int i = 1; i <= room.getCapacity(); i++) {
            beds.add(new DormBed(room.getDormNumber(), String.valueOf(i), "ACTIVE"));
        }
    }

    public synchronized String normalizeBuildingNumber(String value) {
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

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
