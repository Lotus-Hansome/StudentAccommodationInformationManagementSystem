package com.dormitory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MysqlDormInfrastructureRepository implements DormInfrastructureRepository {
    private final MysqlConnectionFactory connectionFactory;

    public MysqlDormInfrastructureRepository(MysqlConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public List<Building> loadBuildings() throws IOException {
        String sql = """
                SELECT building_number, building_name, gender_type, total_floors, status
                FROM buildings
                ORDER BY CAST(building_number AS UNSIGNED), building_number
                """;
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<Building> buildings = new ArrayList<>();
            while (resultSet.next()) {
                buildings.add(new Building(
                        resultSet.getString("building_number"),
                        resultSet.getString("building_name"),
                        resultSet.getString("gender_type"),
                        resultSet.getInt("total_floors"),
                        resultSet.getString("status")));
            }
            return buildings;
        } catch (SQLException e) {
            throw new IOException("读取楼栋失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<DormRoom> loadRooms() throws IOException {
        String sql = """
                SELECT dorm_number, building_number, floor_number, room_type, gender_type, capacity, phone, status
                FROM dorm_rooms
                ORDER BY CAST(building_number AS UNSIGNED), building_number, floor_number, dorm_number
                """;
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<DormRoom> rooms = new ArrayList<>();
            while (resultSet.next()) {
                rooms.add(new DormRoom(
                        resultSet.getString("dorm_number"),
                        resultSet.getString("building_number"),
                        resultSet.getInt("floor_number"),
                        resultSet.getString("room_type"),
                        resultSet.getString("gender_type"),
                        resultSet.getInt("capacity"),
                        resultSet.getString("phone"),
                        resultSet.getString("status")));
            }
            return rooms;
        } catch (SQLException e) {
            throw new IOException("读取宿舍失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<DormBed> loadBeds() throws IOException {
        String sql = """
                SELECT dorm_number, bed_number, status
                FROM beds
                ORDER BY dorm_number, CAST(bed_number AS UNSIGNED), bed_number
                """;
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<DormBed> beds = new ArrayList<>();
            while (resultSet.next()) {
                beds.add(new DormBed(
                        resultSet.getString("dorm_number"),
                        resultSet.getString("bed_number"),
                        resultSet.getString("status")));
            }
            return beds;
        } catch (SQLException e) {
            throw new IOException("读取床位失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void upsertBuilding(Building building) throws IOException {
        String sql = """
                INSERT INTO buildings (building_number, building_name, gender_type, total_floors, status)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  building_name = VALUES(building_name),
                  gender_type = VALUES(gender_type),
                  total_floors = VALUES(total_floors),
                  status = VALUES(status)
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, building.getBuildingNumber());
            statement.setString(2, building.getBuildingName());
            statement.setString(3, building.getGenderType());
            statement.setInt(4, building.getTotalFloors());
            statement.setString(5, building.getStatus());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("保存楼栋失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void upsertRoom(DormRoom room) throws IOException {
        String roomSql = """
                INSERT INTO dorm_rooms (dorm_number, building_number, floor_number, room_type, gender_type, capacity, phone, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  building_number = VALUES(building_number),
                  floor_number = VALUES(floor_number),
                  room_type = VALUES(room_type),
                  gender_type = VALUES(gender_type),
                  capacity = VALUES(capacity),
                  phone = VALUES(phone),
                  status = VALUES(status)
                """;
        String deleteBedsSql = "DELETE FROM beds WHERE dorm_number = ?";
        String insertBedSql = """
                INSERT INTO beds (dorm_number, bed_number, status)
                VALUES (?, ?, 'ACTIVE')
                """;
        Connection connection = null;
        try {
            connection = connectionFactory.openConnection();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(roomSql)) {
                statement.setString(1, room.getDormNumber());
                statement.setString(2, room.getBuildingNumber());
                statement.setInt(3, room.getFloorNumber());
                statement.setString(4, room.getRoomType());
                statement.setString(5, room.getGenderType());
                statement.setInt(6, room.getCapacity());
                statement.setString(7, room.getPhone());
                statement.setString(8, room.getStatus());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(deleteBedsSql)) {
                statement.setString(1, room.getDormNumber());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(insertBedSql)) {
                for (int i = 1; i <= room.getCapacity(); i++) {
                    statement.setString(1, room.getDormNumber());
                    statement.setString(2, String.valueOf(i));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly(connection);
            throw new IOException("保存宿舍失败：" + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
