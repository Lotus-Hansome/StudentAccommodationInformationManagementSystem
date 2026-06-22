package com.dormitory;

import java.nio.file.Path;

public final class SmokeTestRunner {
    private SmokeTestRunner() {
    }

    public static void run() {
        Path dataDirectory = Path.of("build", "smoke-data-" + System.currentTimeMillis());
        StudentDormService studentService = new StudentDormService(new CsvStudentRepository(dataDirectory, false));
        ChangeRequestService requestService = new ChangeRequestService(new CsvChangeRequestRepository(dataDirectory), studentService);

        studentService.add(new StudentDormRecord("T001", "测试甲", "计算机系", "软件测试1班", "9-101", "0571-9101", "1"));
        studentService.add(new StudentDormRecord("T002", "测试乙", "计算机系", "软件测试1班", "9-101", "0571-9101", "2"));
        studentService.add(new StudentDormRecord("T003", "测试丙", "外语系", "英语测试1班", "9-102", "0571-9102", "1"));

        require(studentService.findByStudentId("T001").isPresent(), "按学号查询失败");
        require(studentService.findByDormNumber("9-101").size() == 2, "按宿舍查询失败");
        require(studentService.sortByDepartmentAndClass().size() == 3, "排序显示失败");

        DormChangeRequest request = requestService.submit("T003", "9-101", "0571-9101", "3", "靠近实验室便于晚间调试");
        requestService.approve(request.getId(), "床位空余，同意调换");
        StudentDormRecord updated = studentService.findByStudentId("T003").orElseThrow();
        require("9-101".equals(updated.getDormNumber()) && "3".equals(updated.getBedNumber()), "调换审批后宿舍未更新");

        DormStatistics statistics = studentService.statisticsByBuilding("9");
        String analysis = new LocalRuleDormAnalyzer().analyze(statistics);
        require(analysis.contains("入住率"), "智能分析结果异常");

        System.out.println("SMOKE_TEST_PASSED");
        System.out.println(statistics.toPromptText());
        System.out.println(analysis);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
