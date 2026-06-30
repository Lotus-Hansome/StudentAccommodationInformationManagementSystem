package com.dormitory;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SmokeTestRunner {
    private SmokeTestRunner() {
    }

    public static void run() {
        Path dataDirectory = Path.of("build", "smoke-data-" + System.currentTimeMillis());
        DormInfrastructureService infrastructureService =
                new DormInfrastructureService(new InMemoryDormInfrastructureRepository());
        StudentDormService studentService =
                new StudentDormService(new CsvStudentRepository(dataDirectory, false), infrastructureService);
        ChangeRequestService requestService = new ChangeRequestService(new CsvChangeRequestRepository(dataDirectory), studentService);

        studentService.add(new StudentDormRecord("T001", "Test A", "Computer", "Software Test 1", "9-101", "0571-9101", "1"));
        studentService.add(new StudentDormRecord("T002", "Test B", "Computer", "Software Test 1", "9-101", "0571-9101", "2"));
        studentService.add(new StudentDormRecord("T003", "Test C", "Foreign Language", "English Test 1", "9-102", "0571-9102", "1"));

        require(studentService.findByStudentId(" T001 ").isPresent(), "student lookup should trim input");
        require(studentService.findByDormNumber("9-101").size() == 2, "dorm lookup failed");
        require(studentService.findByDepartmentAndClass("Computer", "").size() == 2, "department lookup failed");
        require(studentService.findByDepartmentAndClass("", "Software").size() == 2, "class lookup failed");
        require(studentService.findByDepartmentAndClass("Computer", "Software Test 1").size() == 2, "department and class lookup failed");
        require(studentService.sortByDepartmentAndClass().size() == 3, "sort failed");
        require(!studentService.deleteByDormAndStudent("0-000", "NO_SUCH"), "missing delete should return false");
        require(studentService.buildingOccupancySummaries().size() == 1, "building occupancy summary failed");
        require(studentService.dormOccupancySummaries().size() == 3, "dorm occupancy summary should include empty rooms");
        require(studentService.buildingOccupancySummaries("9").size() == 1, "filtered building occupancy summary failed");
        require(studentService.dormOccupancySummaries("9-101").size() == 1, "filtered dorm occupancy summary failed");
        require(infrastructureService.activeBedNumbers("9-101").equals(List.of("1", "2", "3", "4")),
                "active room bed numbers should be available for vacancy display");
        DormOccupancySummary buildingSummary = studentService.buildingOccupancySummaries().get(0);
        require(buildingSummary.getRoomCount() == 3 && buildingSummary.getTotalCapacity() == 12 && buildingSummary.getVacantBeds() == 9,
                "building vacant bed calculation failed");
        DormStatistics initialStatistics = studentService.statisticsByBuilding("9");
        require(initialStatistics.getRoomCount() == 3,
                "building statistics should include empty rooms");
        DormStatistics missingDormStatistics = studentService.statisticsByDorm("9-999");
        require(missingDormStatistics.getRoomCount() == 0 && missingDormStatistics.getTotalCapacity() == 0,
                "missing dorm statistics should not invent capacity");
        expectFailure(
                () -> studentService.validateRoomChange(
                        new DormRoom("9-101", "9", 1, "Test room", "MIXED", 1, "0571-9101", "ACTIVE")),
                "room capacity must not exclude an occupied bed");
        expectFailure(
                () -> infrastructureService.saveRoom(
                        new DormRoom("8-101", "9", 1, "Test room", "MIXED", 4, "0571-8101", "ACTIVE")),
                "room number must match its building");
        expectFailure(
                () -> infrastructureService.saveRoom(
                        new DormRoom("9-104", "9", 1, "Test room", "MIXED", 4, "0571-9104", "UNKNOWN")),
                "room status should only accept supported values");

        expectFailure(
                () -> requestService.submit("T003", "9-103", "0571-9103", "1", " "),
                "blank reason should be rejected");

        DormChangeRequest request = requestService.submit("T003", "9-101", "", "3", "Closer to the lab");
        require("0571-9101".equals(request.getTargetDormPhone()),
                "change request should use the room phone from infrastructure");
        expectFailure(
                () -> requestService.submit("T003", "9-103", "0571-9103", "1", "Second pending request"),
                "duplicate pending request should be rejected");
        expectFailure(
                () -> requestService.submit("T002", "9-101", "0571-9101", "3", "Same target bed"),
                "pending target bed should be locked");
        expectFailure(
                () -> requestService.validateBedAssignment("9-101", "3"),
                "administrator assignment should respect a pending target-bed lock");
        expectFailure(
                () -> requestService.approve(request.getId(), " "),
                "blank approval comment should be rejected");

        requestService.approve(request.getId(), "Approved");
        StudentDormRecord updated = studentService.findByStudentId("T003").orElseThrow();
        require("9-101".equals(updated.getDormNumber()) && "3".equals(updated.getBedNumber()), "approved request should update dorm");

        DormChangeRequest cancelRequest = requestService.submit("T001", "9-102", "0571-9102", "2", "Need a quiet room");
        requestService.cancel(cancelRequest.getId(), "T001");
        require(requestService.listByStudentId("T001").stream().anyMatch(item -> item.getStatus() == ChangeRequestStatus.CANCELED),
                "student should be able to cancel a pending request");

        RepairReportService repairService = new RepairReportService(new InMemoryRepairReportRepository());
        RepairReport repair = repairService.submit("T001", "9-101", "Network", "Network port is unavailable");
        require(repairService.listByStudentId("T001").size() == 1, "student repair report should be saved");
        repairService.updateStatus(repair.getId(), "DONE", "Fixed");
        require(repairService.listByStudentId("T001").get(0).getStatus() == RepairStatus.DONE,
                "repair status should be updateable");
        RepairReport cancelRepair = repairService.submit("T001", "9-101", "Furniture", "Desk screw is loose");
        repairService.cancel(cancelRepair.getId(), "T001");
        require(repairService.listByStudentId("T001").stream().anyMatch(item -> item.getStatus() == RepairStatus.CANCELED),
                "student should be able to cancel an active repair report");

        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        UserService userService = new UserService(userRepository, studentService);
        userService.create("admin2", "admin234", "ADMIN", "", true);
        long enabledAdmins = userService.listAll().stream()
                .filter(user -> user.getRole() == UserRole.ADMIN && user.isEnabled())
                .count();
        require(enabledAdmins == 2, "system should allow multiple administrators");
        expectFailure(
                () -> userService.create("invalid-admin", "admin234", "ADMIN", "T001", true),
                "administrator should not be bound to a student");
        expectFailure(
                () -> userService.create("", "student234", "USER", "NO_SUCH_STUDENT", true),
                "student account should only bind to an existing student record");
        expectFailure(
                () -> userService.update("admin2", "ADMIN", "T002", true, "admin"),
                "administrator update should reject a student binding");
        expectFailure(
                () -> userService.delete("admin", "admin"),
                "current administrator should not be deletable");
        userService.create("ignored-student-name", "student234", "USER", "T003", true);
        require(userService.listAll().stream()
                        .anyMatch(user -> user.getUsername().equals("T003") && user.getStudentId().equals("T003")),
                "student username should be generated from the student id");
        require(new AuthService(userRepository).login("T003", "student234").isPresent(),
                "student should be able to log in with the student id");
        expectFailure(
                () -> userService.create("student3", "student345", "USER", "T003", true),
                "a student should only be bound to one account");
        userService.delete("T003", "admin");
        require(userService.listAll().stream().noneMatch(user -> user.getUsername().equals("T003")),
                "ordinary user should be deletable");
        userService.update("admin", "USER", "T001", true, "admin");
        expectFailure(
                () -> userService.update("admin2", "USER", "T002", true, "admin2"),
                "last enabled administrator should not be demoted");
        expectFailure(
                () -> userService.update("admin2", "ADMIN", "", false, "admin"),
                "last enabled administrator should not be disabled");
        expectFailure(
                () -> userService.delete("admin2", "admin"),
                "last enabled administrator should not be deleted");

        DormStatistics statistics = studentService.statisticsByBuilding("9");
        String analysis = new LocalRuleDormAnalyzer().analyze(statistics);
        require(analysis.contains("入住率"), "analysis should include occupancy rate");
        verifyOpenAiCompatibleAnalysis(statistics);

        System.out.println("SMOKE_TEST_PASSED");
        System.out.println(statistics.toPromptText());
        System.out.println(analysis);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void verifyOpenAiCompatibleAnalysis(DormStatistics statistics) {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("could not start local model mock", e);
        }
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            String requestText = new String(requestBody, StandardCharsets.UTF_8);
            require(requestText.contains("\"model\":\"mock-model\""), "model request should include configured model");
            require(requestText.contains("总床位"), "model request should include dorm statistics");
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"模型分析链路正常\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();
        try {
            ModelServiceConfig config = new ModelServiceConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions",
                    "mock-api-key",
                    "mock-model",
                    "smoke-test");
            String result = new OpenAiCompatibleDormAnalyzer(config).analyze(statistics);
            require("模型分析链路正常".equals(result), "model response parsing failed");
        } finally {
            server.stop(0);
        }
    }

    private static class InMemoryUserRepository implements UserRepository {
        private final List<User> users = new ArrayList<>();

        private InMemoryUserRepository() {
            users.add(new User("admin", PasswordHasher.hash("admin123"), UserRole.ADMIN, "", true));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return users.stream()
                    .filter(user -> user.getUsername().equalsIgnoreCase(username))
                    .findFirst();
        }

        @Override
        public Optional<User> findByLoginId(String loginId) {
            return users.stream()
                    .filter(user -> user.getUsername().equalsIgnoreCase(loginId)
                            || (user.getRole() == UserRole.USER
                            && user.getStudentId().equalsIgnoreCase(loginId)))
                    .findFirst();
        }

        @Override
        public List<User> listAll() {
            return List.copyOf(users);
        }

        @Override
        public void create(User user) {
            users.add(user);
        }

        @Override
        public void update(User user) {
            users.removeIf(item -> item.getUsername().equalsIgnoreCase(user.getUsername()));
            users.add(user);
        }

        @Override
        public void delete(String username) {
            users.removeIf(user -> user.getUsername().equalsIgnoreCase(username));
        }

        @Override
        public void updatePassword(String username, String passwordHash) {
            findByUsername(username).ifPresent(user -> update(new User(
                    user.getUsername(),
                    passwordHash,
                    user.getRole(),
                    user.getStudentId(),
                    user.isEnabled())));
        }

        @Override
        public void updateLastLogin(String username) {
        }
    }

    private static class InMemoryRepairReportRepository implements RepairReportRepository {
        private List<RepairReport> reports = new ArrayList<>();

        @Override
        public List<RepairReport> load() {
            return new ArrayList<>(reports);
        }

        @Override
        public void save(List<RepairReport> reports) {
            this.reports = new ArrayList<>(reports);
        }
    }

    private static class InMemoryDormInfrastructureRepository implements DormInfrastructureRepository {
        private final List<Building> buildings = new ArrayList<>();
        private final List<DormRoom> rooms = new ArrayList<>();
        private final List<DormBed> beds = new ArrayList<>();

        private InMemoryDormInfrastructureRepository() {
            buildings.add(new Building("9", "Test Building", "MIXED", 6, "ACTIVE"));
            upsertRoomUnchecked(new DormRoom("9-101", "9", 1, "Test room", "MIXED", 4, "0571-9101", "ACTIVE"));
            upsertRoomUnchecked(new DormRoom("9-102", "9", 1, "Test room", "MIXED", 4, "0571-9102", "ACTIVE"));
            upsertRoomUnchecked(new DormRoom("9-103", "9", 1, "Test room", "MIXED", 4, "0571-9103", "ACTIVE"));
        }

        @Override
        public List<Building> loadBuildings() {
            return new ArrayList<>(buildings);
        }

        @Override
        public List<DormRoom> loadRooms() {
            return new ArrayList<>(rooms);
        }

        @Override
        public List<DormBed> loadBeds() {
            return new ArrayList<>(beds);
        }

        @Override
        public void upsertBuilding(Building building) {
            buildings.removeIf(item -> item.getBuildingNumber().equalsIgnoreCase(building.getBuildingNumber()));
            buildings.add(building);
        }

        @Override
        public void upsertRoom(DormRoom room) {
            upsertRoomUnchecked(room);
        }

        private void upsertRoomUnchecked(DormRoom room) {
            rooms.removeIf(item -> item.getDormNumber().equalsIgnoreCase(room.getDormNumber()));
            rooms.add(room);
            beds.removeIf(item -> item.getDormNumber().equalsIgnoreCase(room.getDormNumber()));
            for (int i = 1; i <= room.getCapacity(); i++) {
                beds.add(new DormBed(room.getDormNumber(), String.valueOf(i), "ACTIVE"));
            }
        }
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new IllegalStateException(message);
    }
}
