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

    public List<Building> listBuildings(String keyword) {
        String query = normalizeText(keyword).toLowerCase(Locale.ROOT);
        return buildings.stream()
                .filter(building -> query.isBlank()
                        || building.getBuildingNumber().toLowerCase(Locale.ROOT).contains(query)
                        || building.getBuildingName().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(Building::getBuildingNumber, this::compareNumbersAsText))
                .collect(Collectors.toList());
    }

    public List<DormRoom> listRooms(String buildingNumber, String keyword) {
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

    public Optional<Building> findBuilding(String buildingNumber) {
        String normalized = normalizeBuildingNumber(buildingNumber);
        return buildings.stream()
                .filter(building -> building.getBuildingNumber().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public Optional<DormRoom> findRoom(String dormNumber) {
        String normalized = normalizeText(dormNumber);
        return rooms.stream()
                .filter(room -> room.getDormNumber().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public boolean hasInfrastructureData() {
        return !buildings.isEmpty() || !rooms.isEmpty();
    }

    public boolean isRoomActive(String dormNumber) {
        return findRoom(dormNumber).map(DormRoom::isActive).orElse(false);
    }

    public boolean isBedActive(String dormNumber, String bedNumber) {
        String normalizedDorm = normalizeText(dormNumber);
        String normalizedBed = normalizeText(bedNumber);
        return beds.stream()
                .anyMatch(bed -> bed.getDormNumber().equalsIgnoreCase(normalizedDorm)
                        && bed.getBedNumber().equalsIgnoreCase(normalizedBed)
                        && bed.isActive());
    }

    public int roomCapacity(String dormNumber) {
        return findRoom(dormNumber).map(DormRoom::getCapacity).orElse(StudentDormService.DEFAULT_BEDS_PER_DORM);
    }

    public String roomPhone(String dormNumber) {
        return findRoom(dormNumber).map(DormRoom::getPhone).orElse("");
    }

    public int buildingCapacity(String buildingNumber) {
        String normalized = normalizeBuildingNumber(buildingNumber);
        return rooms.stream()
                .filter(DormRoom::isActive)
                .filter(room -> room.getBuildingNumber().equalsIgnoreCase(normalized))
                .mapToInt(DormRoom::getCapacity)
                .sum();
    }

    public int totalCapacity() {
        return rooms.stream()
                .filter(DormRoom::isActive)
                .mapToInt(DormRoom::getCapacity)
                .sum();
    }

    public Set<String> activeDormNumbers() {
        return rooms.stream()
                .filter(DormRoom::isActive)
                .map(DormRoom::getDormNumber)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public Map<String, List<DormRoom>> activeRoomsByBuilding() {
        return rooms.stream()
                .filter(DormRoom::isActive)
                .collect(Collectors.groupingBy(DormRoom::getBuildingNumber));
    }

    public void saveBuilding(Building building) {
        validateBuilding(building);
        try {
            repository.upsertBuilding(building);
            replaceBuilding(building);
        } catch (IOException e) {
            throw new IllegalStateException("保存楼栋失败：" + e.getMessage(), e);
        }
    }

    public void saveRoom(DormRoom room) {
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
        if (building.getTotalFloors() <= 0) {
            throw new IllegalArgumentException("楼层数必须大于 0。");
        }
    }

    private void validateRoom(DormRoom room) {
        if (room.getDormNumber().isBlank() || room.getBuildingNumber().isBlank()) {
            throw new IllegalArgumentException("宿舍号和楼栋号不能为空。");
        }
        if (findBuilding(room.getBuildingNumber()).isEmpty()) {
            throw new IllegalArgumentException("楼栋不存在，不能添加宿舍。");
        }
        if (room.getCapacity() <= 0 || room.getCapacity() > 8) {
            throw new IllegalArgumentException("宿舍容量必须在 1 到 8 之间。");
        }
        if (room.getFloorNumber() <= 0) {
            throw new IllegalArgumentException("楼层必须大于 0。");
        }
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

    public String normalizeBuildingNumber(String value) {
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
