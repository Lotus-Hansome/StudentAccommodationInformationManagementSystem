package com.dormitory;

import java.nio.file.Path;

public final class SmokeTestRunner {
    private SmokeTestRunner() {
    }

    public static void run() {
        Path dataDirectory = Path.of("build", "smoke-data-" + System.currentTimeMillis());
        StudentDormService studentService = new StudentDormService(new CsvStudentRepository(dataDirectory, false));
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

        expectFailure(
                () -> requestService.submit("T003", "9-103", "0571-9103", "1", " "),
                "blank reason should be rejected");

        DormChangeRequest request = requestService.submit("T003", "9-101", "0571-9101", "3", "Closer to the lab");
        expectFailure(
                () -> requestService.submit("T003", "9-103", "0571-9103", "1", "Second pending request"),
                "duplicate pending request should be rejected");

        requestService.approve(request.getId(), "Approved");
        StudentDormRecord updated = studentService.findByStudentId("T003").orElseThrow();
        require("9-101".equals(updated.getDormNumber()) && "3".equals(updated.getBedNumber()), "approved request should update dorm");

        DormStatistics statistics = studentService.statisticsByBuilding("9");
        String analysis = new LocalRuleDormAnalyzer().analyze(statistics);
        require(analysis.contains("入住率"), "analysis should include occupancy rate");

        System.out.println("SMOKE_TEST_PASSED");
        System.out.println(statistics.toPromptText());
        System.out.println(analysis);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
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
