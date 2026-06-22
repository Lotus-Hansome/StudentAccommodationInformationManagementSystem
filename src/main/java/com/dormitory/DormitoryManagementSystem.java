package com.dormitory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class DormitoryManagementSystem {
    private final Scanner scanner;
    private final AuthService authService;
    private final StudentDormService studentDormService;
    private final ChangeRequestService changeRequestService;
    private final DormAnalysisService dormAnalysisService;

    public DormitoryManagementSystem(Path dataDirectory) {
        this.scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        this.authService = new AuthService();
        this.studentDormService = new StudentDormService(new CsvStudentRepository(dataDirectory));
        this.changeRequestService = new ChangeRequestService(new CsvChangeRequestRepository(dataDirectory), studentDormService);
        this.dormAnalysisService = new DormAnalysisService();
    }

    public DormitoryManagementSystem(StudentRepository studentRepository, ChangeRequestRepository changeRequestRepository) {
        this.scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        this.authService = new AuthService();
        this.studentDormService = new StudentDormService(studentRepository);
        this.changeRequestService = new ChangeRequestService(changeRequestRepository, studentDormService);
        this.dormAnalysisService = new DormAnalysisService();
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--smoke-test".equals(args[0])) {
            SmokeTestRunner.run();
            return;
        }
        if (args.length > 0 && "--console".equals(args[0])) {
            MysqlConnectionFactory connectionFactory = MysqlSupport.initializedConnectionFactory();
            new DormitoryManagementSystem(
                    new MysqlStudentRepository(connectionFactory),
                    new MysqlChangeRequestRepository(connectionFactory)).start();
            return;
        }
        if (args.length > 0 && "--desktop".equals(args[0])) {
            DormitoryManagementGui.launch(Path.of("data"));
            return;
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("APP_PORT", "8080"));
        new DormitoryWebServer(port, Path.of("src", "main", "resources", "web")).start();
    }

    public void start() {
        while (true) {
            printTitle("学生宿舍信息管理系统");
            System.out.println("默认账号：admin/admin123，student/student123");
            System.out.println("1. 登录系统");
            System.out.println("0. 退出");
            String choice = readLine("请选择：");
            if ("0".equals(choice)) {
                System.out.println("感谢使用，再见。");
                return;
            }
            if ("1".equals(choice)) {
                login();
            } else {
                System.out.println("输入无效，请重新选择。");
            }
        }
    }

    private void login() {
        String username = readLine("用户名：");
        String password = readLine("密码：");
        Optional<User> user = authService.login(username, password);
        if (user.isEmpty()) {
            System.out.println("登录失败：用户名或密码错误。");
            return;
        }
        if (user.get().getRole() == UserRole.ADMIN) {
            adminMenu();
        } else {
            userMenu();
        }
    }

    private void adminMenu() {
        while (true) {
            printTitle("管理员菜单");
            System.out.println("1. 添加学生宿舍信息");
            System.out.println("2. 删除学生宿舍信息");
            System.out.println("3. 查询学生宿舍信息");
            System.out.println("4. 显示全部宿舍信息");
            System.out.println("5. 按系和班级排序显示");
            System.out.println("6. 审核宿舍调换申请");
            System.out.println("7. 查看全部调换申请");
            System.out.println("8. 宿舍统计与智能分析");
            System.out.println("0. 退出登录");
            String choice = readLine("请选择：");
            try {
                switch (choice) {
                    case "1" -> addStudentDormRecord();
                    case "2" -> deleteStudentDormRecord();
                    case "3" -> queryStudentDormRecord();
                    case "4" -> printRecords(studentDormService.listAll());
                    case "5" -> printRecords(studentDormService.sortByDepartmentAndClass());
                    case "6" -> reviewChangeRequests();
                    case "7" -> printRequests(changeRequestService.listAll());
                    case "8" -> showStatisticsAndAnalysis();
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("输入无效，请重新选择。");
                }
            } catch (RuntimeException e) {
                System.out.println("操作失败：" + e.getMessage());
            }
        }
    }

    private void userMenu() {
        while (true) {
            printTitle("普通用户菜单");
            System.out.println("1. 按宿舍号查询");
            System.out.println("2. 按学号查询");
            System.out.println("3. 显示全部宿舍信息");
            System.out.println("4. 按系和班级排序显示");
            System.out.println("5. 提交宿舍调换申请");
            System.out.println("6. 查看我的调换申请");
            System.out.println("0. 退出登录");
            String choice = readLine("请选择：");
            try {
                switch (choice) {
                    case "1" -> queryByDorm();
                    case "2" -> queryByStudentId();
                    case "3" -> printRecords(studentDormService.listAll());
                    case "4" -> printRecords(studentDormService.sortByDepartmentAndClass());
                    case "5" -> submitChangeRequest();
                    case "6" -> {
                        String studentId = readLine("请输入学号：");
                        printRequests(changeRequestService.listByStudentId(studentId));
                    }
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("输入无效，请重新选择。");
                }
            } catch (RuntimeException e) {
                System.out.println("操作失败：" + e.getMessage());
            }
        }
    }

    private void addStudentDormRecord() {
        printTitle("添加学生宿舍信息");
        StudentDormRecord record = new StudentDormRecord(
                readLine("学号："),
                readLine("姓名："),
                readLine("所在系："),
                readLine("班级："),
                readLine("宿舍号（如 3-501）："),
                readLine("宿舍电话："),
                readLine("床位："));
        studentDormService.add(record);
        System.out.println("添加成功。");
    }

    private void deleteStudentDormRecord() {
        printTitle("删除学生宿舍信息");
        String dormNumber = readLine("宿舍号：");
        String studentId = readLine("学号：");
        boolean removed = studentDormService.deleteByDormAndStudent(dormNumber, studentId);
        System.out.println(removed ? "删除成功。" : "未找到匹配记录。");
    }

    private void queryStudentDormRecord() {
        printTitle("查询学生宿舍信息");
        System.out.println("1. 按宿舍号查询");
        System.out.println("2. 按学号查询");
        String choice = readLine("请选择：");
        if ("1".equals(choice)) {
            queryByDorm();
        } else if ("2".equals(choice)) {
            queryByStudentId();
        } else {
            System.out.println("输入无效。");
        }
    }

    private void queryByDorm() {
        String dormNumber = readLine("宿舍号：");
        printRecords(studentDormService.findByDormNumber(dormNumber));
    }

    private void queryByStudentId() {
        String studentId = readLine("学号：");
        Optional<StudentDormRecord> record = studentDormService.findByStudentId(studentId);
        printRecords(record.map(List::of).orElseGet(List::of));
    }

    private void submitChangeRequest() {
        printTitle("提交宿舍调换申请");
        DormChangeRequest request = changeRequestService.submit(
                readLine("学号："),
                readLine("目标宿舍号："),
                readLine("目标宿舍电话："),
                readLine("目标床位："),
                readLine("调换理由："));
        System.out.println("申请提交成功，申请编号：" + request.getId());
    }

    private void reviewChangeRequests() {
        printTitle("审核宿舍调换申请");
        List<DormChangeRequest> pending = changeRequestService.listPending();
        printRequests(pending);
        if (pending.isEmpty()) {
            return;
        }
        String id = readLine("请输入要审核的申请编号：");
        System.out.println("1. 同意");
        System.out.println("2. 拒绝");
        String action = readLine("请选择：");
        String comment = readLine("审核意见：");
        if ("1".equals(action)) {
            changeRequestService.approve(id, comment);
            System.out.println("已同意申请，学生宿舍信息已自动更新。");
        } else if ("2".equals(action)) {
            changeRequestService.reject(id, comment);
            System.out.println("已拒绝申请。");
        } else {
            System.out.println("输入无效，未处理申请。");
        }
    }

    private void showStatisticsAndAnalysis() {
        printTitle("宿舍统计与智能分析");
        System.out.println("1. 按楼栋统计");
        System.out.println("2. 按宿舍统计");
        String choice = readLine("请选择：");
        DormStatistics statistics;
        if ("1".equals(choice)) {
            statistics = studentDormService.statisticsByBuilding(readLine("楼栋号（如 3）："));
        } else if ("2".equals(choice)) {
            statistics = studentDormService.statisticsByDorm(readLine("宿舍号（如 3-501）："));
        } else {
            System.out.println("输入无效。");
            return;
        }
        printStatistics(statistics);
        System.out.println("\n智能宿舍运营评估与建议：");
        System.out.println(dormAnalysisService.analyze(statistics));
    }

    private void printRecords(List<StudentDormRecord> records) {
        if (records.isEmpty()) {
            System.out.println("暂无学生宿舍信息。");
            return;
        }
        System.out.printf("%-12s %-8s %-12s %-12s %-10s %-14s %-6s%n",
                "学号", "姓名", "所在系", "班级", "宿舍号", "宿舍电话", "床位");
        for (StudentDormRecord record : records) {
            System.out.printf("%-12s %-8s %-12s %-12s %-10s %-14s %-6s%n",
                    record.getStudentId(),
                    record.getName(),
                    record.getDepartment(),
                    record.getClassName(),
                    record.getDormNumber(),
                    record.getDormPhone(),
                    record.getBedNumber());
        }
    }

    private void printRequests(List<DormChangeRequest> requests) {
        if (requests.isEmpty()) {
            System.out.println("暂无宿舍调换申请。");
            return;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        System.out.printf("%-22s %-10s %-10s %-8s %-10s %-8s %-8s %-16s %-20s%n",
                "申请编号", "学号", "原宿舍", "原床位", "目标宿舍", "目标床位", "状态", "申请时间", "理由/意见");
        for (DormChangeRequest request : requests) {
            String handledText = request.getAdminComment() == null || request.getAdminComment().isBlank()
                    ? request.getReason()
                    : request.getReason() + " / " + request.getAdminComment();
            System.out.printf("%-22s %-10s %-10s %-8s %-10s %-8s %-8s %-16s %-20s%n",
                    request.getId(),
                    request.getStudentId(),
                    request.getCurrentDormNumber(),
                    request.getCurrentBedNumber(),
                    request.getTargetDormNumber(),
                    request.getTargetBedNumber(),
                    request.getStatus().getDisplayName(),
                    request.getCreatedAt().format(formatter),
                    handledText);
        }
    }

    private void printStatistics(DormStatistics statistics) {
        System.out.println(statistics.toPromptText());
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("========== " + title + " ==========");
    }
}
