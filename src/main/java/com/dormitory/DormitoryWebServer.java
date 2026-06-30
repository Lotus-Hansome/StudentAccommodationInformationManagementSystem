package com.dormitory;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class DormitoryWebServer {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int SESSION_TTL_MINUTES = 120;
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_WINDOW_MILLIS = 10 * 60 * 1000L;

    private final AuthService authService;
    private final UserService userService;
    private final StudentDormService studentDormService;
    private final ChangeRequestService changeRequestService;
    private final RepairReportService repairReportService;
    private final DormAnalysisService dormAnalysisService;
    private final ModelConfigService modelConfigService;
    private final DormInfrastructureService infrastructureService;
    private final OperationLogService operationLogService;
    private final Map<String, Session> sessions;
    private final Map<String, LoginAttempt> loginAttempts;
    private final Path webRoot;
    private final int port;

    public DormitoryWebServer(int port, Path webRoot) {
        MysqlConnectionFactory connectionFactory = MysqlSupport.initializedConnectionFactory();
        MysqlUserRepository userRepository = new MysqlUserRepository(connectionFactory);
        this.infrastructureService = new DormInfrastructureService(new MysqlDormInfrastructureRepository(connectionFactory));
        this.studentDormService = new StudentDormService(new MysqlStudentRepository(connectionFactory), infrastructureService);
        this.authService = new AuthService(userRepository);
        this.userService = new UserService(userRepository, studentDormService);
        this.changeRequestService = new ChangeRequestService(new MysqlChangeRequestRepository(connectionFactory), studentDormService);
        this.repairReportService = new RepairReportService(new MysqlRepairReportRepository(connectionFactory));
        this.dormAnalysisService = new DormAnalysisService();
        this.modelConfigService = new ModelConfigService();
        this.operationLogService = new OperationLogService(new MysqlOperationLogRepository(connectionFactory));
        this.sessions = new ConcurrentHashMap<>();
        this.loginAttempts = new ConcurrentHashMap<>();
        this.webRoot = webRoot;
        this.port = port;
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newFixedThreadPool(12));
            server.start();
            System.out.println("学生宿舍信息管理系统已启动：http://localhost:" + port);
            System.out.println("数据存储：MySQL；管理员初始账号：admin/admin123；学生账号为学号，初始密码 student123");
        } catch (IOException e) {
            if (e instanceof BindException) {
                throw new IllegalStateException("端口 " + port + " 已被占用。请先停止旧服务，或设置 APP_PORT 使用其他端口。", e);
            }
            throw new IllegalStateException("启动 Web 服务失败：" + e.getMessage(), e);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else {
                serveStatic(exchange, path);
            }
        } catch (ApiException e) {
            sendJson(exchange, e.statusCode, "{\"success\":false,\"message\":" + WebJson.quote(e.getMessage()) + "}");
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"success\":false,\"message\":" + WebJson.quote(e.getMessage()) + "}");
        } catch (RuntimeException e) {
            e.printStackTrace(System.err);
            sendJson(exchange, 500, "{\"success\":false,\"message\":\"系统处理请求失败，请稍后重试。\"}");
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        if ("/api/login".equals(path) && "POST".equalsIgnoreCase(method)) {
            login(exchange);
            return;
        }
        if ("/api/logout".equals(path) && "POST".equalsIgnoreCase(method)) {
            logout(exchange);
            return;
        }
        if ("/api/overview".equals(path) && "GET".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            overview(exchange);
            return;
        }
        if ("/api/student-home".equals(path) && "GET".equalsIgnoreCase(method)) {
            studentHome(exchange);
            return;
        }
        if ("/api/students".equals(path)) {
            students(exchange, method);
            return;
        }
        if ("/api/requests".equals(path)) {
            requests(exchange, method);
            return;
        }
        if ("/api/repairs/status".equals(path) && "POST".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            repairDecision(exchange);
            return;
        }
        if ("/api/repairs/cancel".equals(path) && "POST".equalsIgnoreCase(method)) {
            cancelRepair(exchange);
            return;
        }
        if ("/api/repairs".equals(path)) {
            repairs(exchange, method);
            return;
        }
        if ("/api/requests/approve".equals(path) && "POST".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            requestDecision(exchange, true);
            return;
        }
        if ("/api/requests/reject".equals(path) && "POST".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            requestDecision(exchange, false);
            return;
        }
        if ("/api/requests/cancel".equals(path) && "POST".equalsIgnoreCase(method)) {
            cancelRequest(exchange);
            return;
        }
        if ("/api/statistics".equals(path) && "GET".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            statistics(exchange);
            return;
        }
        if ("/api/occupancy".equals(path) && "GET".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            occupancy(exchange);
            return;
        }
        if ("/api/buildings".equals(path)) {
            requireAdmin(exchange);
            buildings(exchange, method);
            return;
        }
        if ("/api/rooms".equals(path)) {
            requireAdmin(exchange);
            rooms(exchange, method);
            return;
        }
        if ("/api/users/change-password".equals(path) && "POST".equalsIgnoreCase(method)) {
            changePassword(exchange);
            return;
        }
        if ("/api/users/reset-password".equals(path) && "POST".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            resetPassword(exchange);
            return;
        }
        if ("/api/users".equals(path)) {
            requireAdmin(exchange);
            users(exchange, method);
            return;
        }
        if ("/api/audit-logs".equals(path) && "GET".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            auditLogs(exchange);
            return;
        }
        if ("/api/model-config".equals(path)) {
            User admin = requireAdmin(exchange);
            modelConfig(exchange, method, admin);
            return;
        }
        throw new ApiException(404, "接口不存在。");
    }

    private void login(HttpExchange exchange) throws IOException {
        cleanupExpiredSecurityState();
        Map<String, String> form = readForm(exchange);
        String username = form.getOrDefault("username", "").trim();
        String password = form.getOrDefault("password", "");
        String key = loginKey(exchange, username);
        if (isLoginRateLimited(key)) {
            throw new ApiException(429, "登录失败次数过多，请稍后再试。");
        }
        Optional<User> user = authService.login(username, password);
        if (user.isEmpty()) {
            recordLoginFailure(key);
            throw new ApiException(401, "登录账号或密码错误，或账号已被禁用。");
        }
        loginAttempts.remove(key);
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(user.get(), LocalDateTime.now().plusMinutes(SESSION_TTL_MINUTES)));
        sendJson(exchange, 200, "{"
                + WebJson.booleanProperty("success", true) + ","
                + WebJson.property("token", token) + ","
                + WebJson.property("username", user.get().getUsername()) + ","
                + WebJson.property("role", user.get().getRole().name()) + ","
                + WebJson.property("studentId", user.get().getStudentId())
                + "}");
    }

    private void logout(HttpExchange exchange) throws IOException {
        String token = exchange.getRequestHeaders().getFirst("X-Auth-Token");
        if (token != null && !token.isBlank()) {
            sessions.remove(token);
        }
        sendJson(exchange, 200, "{" + WebJson.booleanProperty("success", true) + "}");
    }

    private void overview(HttpExchange exchange) throws IOException {
        DormStatistics statistics = studentDormService.statisticsAll();
        long pending = changeRequestService.listPending().size();
        sendJson(exchange, 200, "{"
                + WebJson.booleanProperty("success", true) + ","
                + WebJson.numberProperty("totalStudents", statistics.getTotalStudents()) + ","
                + WebJson.numberProperty("roomCount", statistics.getRoomCount()) + ","
                + WebJson.numberProperty("totalCapacity", statistics.getTotalCapacity()) + ","
                + WebJson.numberProperty("vacantBeds", statistics.getVacantBeds()) + ","
                + WebJson.numberProperty("occupancyRate", Math.round(statistics.getOccupancyRate() * 10) / 10.0) + ","
                + WebJson.numberProperty("pendingRequests", pending) + ","
                + "\"departments\":" + WebJson.departmentCounts(statistics.getDepartmentCounts(), statistics.getTotalStudents())
                + "}");
    }

    private void studentHome(HttpExchange exchange) throws IOException {
        User user = requireUser(exchange);
        String studentId = requireBoundStudent(user);
        StudentDormRecord student = studentDormService.findByStudentId(studentId)
                .orElseThrow(() -> new ApiException(404, "未找到当前学生住宿信息。"));
        List<StudentDormRecord> roommates = studentDormService.findByDormNumber(student.getDormNumber());
        List<DormChangeRequest> requests = changeRequestService.listByStudentId(studentId);
        List<RepairReport> repairs = repairReportService.listByStudentId(studentId);
        sendJson(exchange, 200, "{"
                + WebJson.booleanProperty("success", true) + ","
                + "\"student\":" + studentJson(student, false) + ","
                + "\"roommates\":" + studentsJson(roommates, false) + ","
                + "\"requests\":" + requestsJson(requests) + ","
                + "\"repairs\":" + repairsJson(repairs)
                + "}");
    }

    private void students(HttpExchange exchange, String method) throws IOException {
        User user = requireUser(exchange);
        if ("GET".equalsIgnoreCase(method)) {
            StudentSearchCriteria criteria = criteriaFromQuery(parseQuery(exchange.getRequestURI().getRawQuery()));
            if (user.getRole() != UserRole.ADMIN) {
                criteria.setStudentId(requireBoundStudent(user));
            }
            PageResult<StudentDormRecord> page = studentDormService.search(criteria);
            sendJson(exchange, 200, "{"
                    + WebJson.booleanProperty("success", true) + ","
                    + "\"students\":" + studentsJson(page.getItems(), user.getRole() == UserRole.ADMIN) + ","
                    + pageJson(page)
                    + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            User admin = requireAdmin(exchange);
            Map<String, String> form = readForm(exchange);
            StudentDormRecord record = studentFromForm(form);
            changeRequestService.validateBedAssignment(record.getDormNumber(), record.getBedNumber());
            studentDormService.add(record);
            operationLogService.record(admin.getUsername(), "ADD_STUDENT", "student", record.getStudentId(), "新增学生住宿信息");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"添加成功。\"}");
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            User admin = requireAdmin(exchange);
            Map<String, String> form = readForm(exchange);
            StudentDormRecord record = studentFromForm(form);
            boolean changesBed = studentDormService.findByStudentId(record.getStudentId())
                    .map(existing -> !existing.getDormNumber().equalsIgnoreCase(record.getDormNumber().trim())
                            || !existing.getBedNumber().equalsIgnoreCase(record.getBedNumber().trim()))
                    .orElse(true);
            if (changesBed) {
                changeRequestService.validateBedAssignment(record.getDormNumber(), record.getBedNumber());
            }
            studentDormService.update(record);
            operationLogService.record(admin.getUsername(), "UPDATE_STUDENT", "student", record.getStudentId(), "修改学生住宿信息");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"修改成功。\"}");
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            User admin = requireAdmin(exchange);
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String dormNumber = query.getOrDefault("dormNumber", "");
            String studentId = query.getOrDefault("studentId", "");
            boolean removed = studentDormService.deleteByDormAndStudent(dormNumber, studentId);
            if (!removed) {
                throw new ApiException(404, "未找到匹配的住宿记录。");
            }
            userService.disableStudentAccount(studentId).ifPresent(this::invalidateSessions);
            operationLogService.record(admin.getUsername(), "DELETE_STUDENT", "student", studentId, "删除宿舍 " + dormNumber + " 的住宿信息");
            sendJson(exchange, 200, "{\"success\":true,\"removed\":true,\"message\":\"住宿记录已删除；关联学生账号如存在，已同步停用。\"}");
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private void requests(HttpExchange exchange, String method) throws IOException {
        User user = requireUser(exchange);
        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String mode = query.getOrDefault("mode", user.getRole() == UserRole.ADMIN ? "pending" : "student");
            List<DormChangeRequest> requests;
            if ("all".equals(mode)) {
                requireAdmin(exchange);
                requests = changeRequestService.listAll();
            } else if ("pending".equals(mode)) {
                requireAdmin(exchange);
                requests = changeRequestService.listPending();
            } else {
                String studentId = user.getRole() == UserRole.ADMIN
                        ? query.getOrDefault("studentId", "")
                        : requireBoundStudent(user);
                requests = changeRequestService.listByStudentId(studentId);
            }
            int page = parseInt(query.get("page"), 1);
            int pageSize = parseInt(query.get("pageSize"), 10);
            PageResult<DormChangeRequest> result = paginate(requests, page, pageSize);
            sendJson(exchange, 200, "{"
                    + WebJson.booleanProperty("success", true) + ","
                    + "\"requests\":" + requestsJson(result.getItems()) + ","
                    + pageJson(result)
                    + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            Map<String, String> form = readForm(exchange);
            if (user.getRole() != UserRole.ADMIN) {
                form.put("studentId", requireBoundStudent(user));
            }
            DormChangeRequest request = changeRequestService.submit(
                    form.getOrDefault("studentId", ""),
                    form.getOrDefault("targetDormNumber", ""),
                    form.getOrDefault("targetDormPhone", ""),
                    form.getOrDefault("targetBedNumber", ""),
                    form.getOrDefault("reason", ""));
            operationLogService.record(user.getUsername(), "SUBMIT_REQUEST", "change_request", request.getId(), "提交宿舍调换申请");
            sendJson(exchange, 200, "{\"success\":true,\"id\":" + WebJson.quote(request.getId()) + "}");
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private void repairs(HttpExchange exchange, String method) throws IOException {
        User user = requireUser(exchange);
        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            List<RepairReport> reports = user.getRole() == UserRole.ADMIN
                    ? repairReportService.listAll()
                    : repairReportService.listByStudentId(requireBoundStudent(user));
            int page = parseInt(query.get("page"), 1);
            int pageSize = parseInt(query.get("pageSize"), 10);
            PageResult<RepairReport> result = paginate(reports, page, pageSize);
            sendJson(exchange, 200, "{"
                    + WebJson.booleanProperty("success", true) + ","
                    + "\"repairs\":" + repairsJson(result.getItems()) + ","
                    + pageJson(result)
                    + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            Map<String, String> form = readForm(exchange);
            String studentId = user.getRole() == UserRole.ADMIN
                    ? form.getOrDefault("studentId", "")
                    : requireBoundStudent(user);
            StudentDormRecord student = studentDormService.findByStudentId(studentId)
                    .orElseThrow(() -> new ApiException(404, "未找到学生住宿信息，不能提交报修。"));
            RepairReport report = repairReportService.submit(
                    student.getStudentId(),
                    student.getDormNumber(),
                    form.getOrDefault("category", ""),
                    form.getOrDefault("description", ""));
            operationLogService.record(user.getUsername(), "SUBMIT_REPAIR", "repair_report", report.getId(), "提交宿舍报修反馈");
            sendJson(exchange, 200, "{\"success\":true,\"id\":" + WebJson.quote(report.getId()) + "}");
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private void repairDecision(HttpExchange exchange) throws IOException {
        User admin = requireAdmin(exchange);
        Map<String, String> form = readForm(exchange);
        String repairId = form.getOrDefault("id", "");
        String status = form.getOrDefault("status", "PROCESSING");
        String comment = form.getOrDefault("comment", "");
        repairReportService.updateStatus(repairId, status, comment);
        operationLogService.record(admin.getUsername(), "UPDATE_REPAIR", "repair_report", repairId, status + "：" + comment);
        sendJson(exchange, 200, "{\"success\":true,\"message\":\"报修反馈已更新。\"}");
    }

    private void cancelRepair(HttpExchange exchange) throws IOException {
        User user = requireUser(exchange);
        Map<String, String> form = readForm(exchange);
        String repairId = form.getOrDefault("id", "");
        String studentId = requireBoundStudent(user);
        repairReportService.cancel(repairId, studentId);
        operationLogService.record(user.getUsername(), "CANCEL_REPAIR", "repair_report", repairId, "学生撤回报修反馈");
        sendJson(exchange, 200, "{\"success\":true,\"message\":\"报修反馈已撤回。\"}");
    }

    private void requestDecision(HttpExchange exchange, boolean approve) throws IOException {
        User admin = requireAdmin(exchange);
        Map<String, String> form = readForm(exchange);
        String requestId = form.getOrDefault("requestId", "");
        String comment = form.getOrDefault("comment", "");
        if (approve) {
            changeRequestService.approve(requestId, comment);
        } else {
            changeRequestService.reject(requestId, comment);
        }
        operationLogService.record(admin.getUsername(), approve ? "APPROVE_REQUEST" : "REJECT_REQUEST", "change_request", requestId, comment);
        sendJson(exchange, 200, "{\"success\":true,\"message\":\"审批完成。\"}");
    }

    private void cancelRequest(HttpExchange exchange) throws IOException {
        User user = requireUser(exchange);
        Map<String, String> form = readForm(exchange);
        String requestId = form.getOrDefault("requestId", "");
        String studentId = user.getRole() == UserRole.ADMIN ? form.getOrDefault("studentId", "") : requireBoundStudent(user);
        changeRequestService.cancel(requestId, studentId);
        operationLogService.record(user.getUsername(), "CANCEL_REQUEST", "change_request", requestId, "撤回宿舍调换申请");
        sendJson(exchange, 200, "{\"success\":true,\"message\":\"申请已撤回。\"}");
    }

    private void statistics(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String scope = query.getOrDefault("scope", "building");
        String value = query.getOrDefault("value", "");
        DormStatistics statistics = "dorm".equals(scope)
                ? studentDormService.statisticsByDorm(value)
                : studentDormService.statisticsByBuilding(value);
        String analysis = dormAnalysisService.analyze(statistics);
        sendJson(exchange, 200, "{"
                + WebJson.booleanProperty("success", true) + ","
                + "\"statistics\":" + statisticsJson(statistics) + ","
                + WebJson.property("analysis", analysis)
                + "}");
    }

    private void occupancy(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String scope = query.getOrDefault("scope", "buildings");
        String value = query.getOrDefault("value", "");
        List<DormOccupancySummary> summaries = "dorms".equals(scope)
                ? studentDormService.dormOccupancySummaries(value)
                : studentDormService.buildingOccupancySummaries(value);
        PageResult<DormOccupancySummary> result = paginate(
                summaries,
                parseInt(query.get("page"), 1),
                parseInt(query.get("pageSize"), 10));
        sendJson(exchange, 200, "{"
                + WebJson.booleanProperty("success", true) + ","
                + WebJson.property("scope", scope) + ","
                + "\"items\":" + occupancyJson(result.getItems()) + ","
                + pageJson(result)
                + "}");
    }

    private void buildings(HttpExchange exchange, String method) throws IOException {
        User admin = requireAdmin(exchange);
        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            PageResult<Building> result = paginate(
                    infrastructureService.listBuildings(query.getOrDefault("keyword", "")),
                    parseInt(query.get("page"), 1),
                    parseInt(query.get("pageSize"), 10));
            sendJson(exchange, 200, "{"
                    + WebJson.booleanProperty("success", true) + ","
                    + "\"buildings\":" + buildingsJson(result.getItems()) + ","
                    + pageJson(result)
                    + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            Building building = buildingFromForm(readForm(exchange));
            infrastructureService.saveBuilding(building);
            operationLogService.record(admin.getUsername(), "SAVE_BUILDING", "building", building.getBuildingNumber(), "保存楼栋基础信息");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"楼栋已保存。\"}");
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private void rooms(HttpExchange exchange, String method) throws IOException {
        User admin = requireAdmin(exchange);
        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            PageResult<DormRoom> result = paginate(infrastructureService.listRooms(
                    query.getOrDefault("buildingNumber", ""),
                    query.getOrDefault("keyword", "")),
                    parseInt(query.get("page"), 1),
                    parseInt(query.get("pageSize"), 10));
            sendJson(exchange, 200, "{"
                    + WebJson.booleanProperty("success", true) + ","
                    + "\"rooms\":" + roomsJson(result.getItems()) + ","
                    + pageJson(result)
                    + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            DormRoom room = roomFromForm(readForm(exchange));
            studentDormService.validateRoomChange(room);
            changeRequestService.validateRoomChange(room);
            infrastructureService.saveRoom(room);
            studentDormService.synchronizeRoomPhone(room);
            operationLogService.record(admin.getUsername(), "SAVE_ROOM", "dorm_room", room.getDormNumber(), "保存宿舍基础信息");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"宿舍已保存。\"}");
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private void users(HttpExchange exchange, String method) throws IOException {
        User admin = requireAdmin(exchange);
        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            PageResult<User> result = paginate(
                    userService.listAll(),
                    parseInt(query.get("page"), 1),
                    parseInt(query.get("pageSize"), 10));
            sendJson(exchange, 200, "{"
                    + WebJson.booleanProperty("success", true) + ","
                    + "\"users\":" + usersJson(result.getItems()) + ","
                    + pageJson(result)
                    + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            Map<String, String> form = readForm(exchange);
            String role = form.getOrDefault("role", "USER");
            String targetId = "ADMIN".equalsIgnoreCase(role)
                    ? form.getOrDefault("username", "")
                    : form.getOrDefault("studentId", "");
            userService.create(
                    form.getOrDefault("username", ""),
                    form.getOrDefault("password", ""),
                    role,
                    form.getOrDefault("studentId", ""),
                    parseBoolean(form.getOrDefault("enabled", "true")));
            operationLogService.record(admin.getUsername(), "ADD_USER", "user", targetId, "新增系统账号");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"用户已创建。\"}");
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            Map<String, String> form = readForm(exchange);
            userService.update(
                    form.getOrDefault("username", ""),
                    form.getOrDefault("role", "USER"),
                    form.getOrDefault("studentId", ""),
                    parseBoolean(form.getOrDefault("enabled", "true")),
                    admin.getUsername());
            invalidateSessions(form.getOrDefault("username", ""), admin.getUsername());
            operationLogService.record(admin.getUsername(), "UPDATE_USER", "user", form.getOrDefault("username", ""), "更新系统账号");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"用户已更新。\"}");
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String username = query.getOrDefault("username", "");
            userService.delete(username, admin.getUsername());
            sessions.entrySet().removeIf(entry ->
                    entry.getValue().user.getUsername().equalsIgnoreCase(username.trim()));
            operationLogService.record(admin.getUsername(), "DELETE_USER", "user", username, "删除系统账号");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"用户已删除。\"}");
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private void changePassword(HttpExchange exchange) throws IOException {
        User user = requireUser(exchange);
        Map<String, String> form = readForm(exchange);
        userService.changePassword(user.getUsername(), form.getOrDefault("oldPassword", ""), form.getOrDefault("newPassword", ""));
        invalidateSessions(user.getUsername());
        operationLogService.record(user.getUsername(), "CHANGE_PASSWORD", "user", user.getUsername(), "修改本人密码");
        sendJson(exchange, 200, "{\"success\":true,\"message\":\"密码已修改，请重新登录。\"}");
    }

    private void resetPassword(HttpExchange exchange) throws IOException {
        User admin = requireAdmin(exchange);
        Map<String, String> form = readForm(exchange);
        String username = form.getOrDefault("username", "");
        if (username.equalsIgnoreCase(admin.getUsername())) {
            throw new IllegalArgumentException("请通过“修改密码”功能修改当前管理员密码。");
        }
        userService.resetPassword(username, form.getOrDefault("newPassword", ""));
        invalidateSessions(username);
        operationLogService.record(admin.getUsername(), "RESET_PASSWORD", "user", username, "重置用户密码");
        sendJson(exchange, 200, "{\"success\":true,\"message\":\"密码已重置。\"}");
    }

    private void auditLogs(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        PageResult<OperationLog> page = operationLogService.search(
                parseInt(query.get("page"), 1),
                parseInt(query.get("pageSize"), 20),
                query.getOrDefault("keyword", ""));
        sendJson(exchange, 200, "{"
                + WebJson.booleanProperty("success", true) + ","
                + "\"logs\":" + logsJson(page.getItems()) + ","
                + pageJson(page)
                + "}");
    }

    private void modelConfig(HttpExchange exchange, String method, User admin) throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, modelConfigJson(modelConfigService.loadStatus()));
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            Map<String, String> form = readForm(exchange);
            String action = form.getOrDefault("action", "save");
            ModelConfigStatus status;
            if ("select".equals(action)) {
                status = modelConfigService.selectConfig(form.getOrDefault("id", "local"));
                operationLogService.record(admin.getUsername(), "SELECT_MODEL_CONFIG", "system_config", "model_service", "选择模型服务配置：" + form.getOrDefault("id", "local"));
            } else {
                status = modelConfigService.saveLocalConfig(
                        form.getOrDefault("id", ""),
                        form.getOrDefault("name", ""),
                        form.getOrDefault("apiUrl", ""),
                        form.getOrDefault("apiKey", ""),
                        form.getOrDefault("model", ""),
                        "true".equalsIgnoreCase(form.getOrDefault("activate", "false")));
                operationLogService.record(admin.getUsername(), "SAVE_MODEL_CONFIG", "system_config", "model_service", "保存模型服务本地配置");
            }
            sendJson(exchange, 200, modelConfigJson(status));
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String id = query.getOrDefault("id", "");
            ModelConfigStatus status = id.isBlank()
                    ? modelConfigService.clearLocalConfig()
                    : modelConfigService.deleteLocalConfig(id);
            operationLogService.record(admin.getUsername(), "DELETE_MODEL_CONFIG", "system_config", "model_service", id.isBlank() ? "清空模型服务本地配置" : "删除模型服务配置：" + id);
            sendJson(exchange, 200, modelConfigJson(status));
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private StudentDormRecord studentFromForm(Map<String, String> form) {
        return new StudentDormRecord(
                form.getOrDefault("studentId", ""),
                form.getOrDefault("name", ""),
                form.getOrDefault("department", ""),
                form.getOrDefault("className", ""),
                form.getOrDefault("dormNumber", ""),
                form.getOrDefault("dormPhone", ""),
                form.getOrDefault("bedNumber", ""));
    }

    private Building buildingFromForm(Map<String, String> form) {
        return new Building(
                form.getOrDefault("buildingNumber", ""),
                form.getOrDefault("buildingName", ""),
                form.getOrDefault("genderType", "MIXED"),
                parseInt(form.get("totalFloors"), 6),
                form.getOrDefault("status", "ACTIVE"));
    }

    private DormRoom roomFromForm(Map<String, String> form) {
        return new DormRoom(
                form.getOrDefault("dormNumber", ""),
                form.getOrDefault("buildingNumber", ""),
                parseInt(form.get("floorNumber"), 1),
                form.getOrDefault("roomType", "标准四人间"),
                form.getOrDefault("genderType", "MIXED"),
                parseInt(form.get("capacity"), 4),
                form.getOrDefault("phone", ""),
                form.getOrDefault("status", "ACTIVE"));
    }

    private StudentSearchCriteria criteriaFromQuery(Map<String, String> query) {
        StudentSearchCriteria criteria = new StudentSearchCriteria();
        String mode = query.getOrDefault("mode", "all");
        criteria.setMode(mode);
        criteria.setPage(parseInt(query.get("page"), 1));
        criteria.setPageSize(parseInt(query.get("pageSize"), 10));
        criteria.setKeyword(query.getOrDefault("keyword", ""));
        criteria.setBuildingNumber(query.getOrDefault("buildingNumber", ""));
        criteria.setDepartment(query.getOrDefault("department", ""));
        criteria.setClassName(query.getOrDefault("className", ""));
        criteria.setSort(query.getOrDefault("sort", "dorm"));
        if ("student".equals(mode)) {
            criteria.setStudentId(query.getOrDefault("studentId", ""));
        } else if ("dorm".equals(mode)) {
            criteria.setDormNumber(query.getOrDefault("dormNumber", ""));
        } else if ("departmentClass".equals(mode)) {
            criteria.setSort("department");
        } else if ("sorted".equals(mode)) {
            criteria.setSort("department");
        } else {
            criteria.setStudentId(query.getOrDefault("studentId", ""));
            criteria.setDormNumber(query.getOrDefault("dormNumber", ""));
        }
        return criteria;
    }

    private String studentsJson(List<StudentDormRecord> records, boolean includeActions) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < records.size(); i++) {
            StudentDormRecord record = records.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append(studentJson(record, includeActions));
        }
        builder.append(']');
        return builder.toString();
    }

    private String studentJson(StudentDormRecord record, boolean includeActions) {
        return "{"
                + WebJson.property("studentId", record.getStudentId()) + ","
                + WebJson.property("name", record.getName()) + ","
                + WebJson.property("department", record.getDepartment()) + ","
                + WebJson.property("className", record.getClassName()) + ","
                + WebJson.property("dormNumber", record.getDormNumber()) + ","
                + WebJson.property("buildingNumber", record.getBuildingNumber()) + ","
                + WebJson.property("dormPhone", record.getDormPhone()) + ","
                + WebJson.property("bedNumber", record.getBedNumber()) + ","
                + WebJson.booleanProperty("editable", includeActions)
                + "}";
    }

    private String requestsJson(List<DormChangeRequest> requests) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < requests.size(); i++) {
            DormChangeRequest request = requests.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append(WebJson.property("id", request.getId())).append(',')
                    .append(WebJson.property("studentId", request.getStudentId())).append(',')
                    .append(WebJson.property("currentDormNumber", request.getCurrentDormNumber())).append(',')
                    .append(WebJson.property("currentBedNumber", request.getCurrentBedNumber())).append(',')
                    .append(WebJson.property("targetDormNumber", request.getTargetDormNumber())).append(',')
                    .append(WebJson.property("targetDormPhone", request.getTargetDormPhone())).append(',')
                    .append(WebJson.property("targetBedNumber", request.getTargetBedNumber())).append(',')
                    .append(WebJson.property("reason", request.getReason())).append(',')
                    .append(WebJson.property("status", request.getStatus().name())).append(',')
                    .append(WebJson.property("statusText", request.getStatus().getDisplayName())).append(',')
                    .append(WebJson.property("createdAt", request.getCreatedAt().format(DATE_TIME_FORMATTER))).append(',')
                    .append(WebJson.property("handledAt", request.getHandledAt() == null ? "" : request.getHandledAt().format(DATE_TIME_FORMATTER))).append(',')
                    .append(WebJson.property("adminComment", request.getAdminComment()))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String repairsJson(List<RepairReport> repairs) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < repairs.size(); i++) {
            RepairReport repair = repairs.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append(WebJson.property("id", repair.getId())).append(',')
                    .append(WebJson.property("studentId", repair.getStudentId())).append(',')
                    .append(WebJson.property("dormNumber", repair.getDormNumber())).append(',')
                    .append(WebJson.property("category", repair.getCategory())).append(',')
                    .append(WebJson.property("description", repair.getDescription())).append(',')
                    .append(WebJson.property("status", repair.getStatus().name())).append(',')
                    .append(WebJson.property("statusText", repair.getStatus().getDisplayName())).append(',')
                    .append(WebJson.property("createdAt", repair.getCreatedAt().format(DATE_TIME_FORMATTER))).append(',')
                    .append(WebJson.property("handledAt", repair.getHandledAt() == null ? "" : repair.getHandledAt().format(DATE_TIME_FORMATTER))).append(',')
                    .append(WebJson.property("adminComment", repair.getAdminComment()))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String statisticsJson(DormStatistics statistics) {
        return "{"
                + WebJson.property("scopeType", statistics.getScopeType()) + ","
                + WebJson.property("scopeValue", statistics.getScopeValue()) + ","
                + WebJson.numberProperty("totalStudents", statistics.getTotalStudents()) + ","
                + WebJson.numberProperty("roomCount", statistics.getRoomCount()) + ","
                + WebJson.numberProperty("totalCapacity", statistics.getTotalCapacity()) + ","
                + WebJson.numberProperty("vacantBeds", statistics.getVacantBeds()) + ","
                + WebJson.numberProperty("occupancyRate", Math.round(statistics.getOccupancyRate() * 10) / 10.0) + ","
                + WebJson.property("promptText", statistics.toPromptText()) + ","
                + "\"departments\":" + WebJson.departmentCounts(statistics.getDepartmentCounts(), statistics.getTotalStudents())
                + "}";
    }

    private String occupancyJson(List<DormOccupancySummary> summaries) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < summaries.size(); i++) {
            DormOccupancySummary summary = summaries.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append(WebJson.property("scope", summary.getScope())).append(',')
                    .append(WebJson.property("buildingNumber", summary.getBuildingNumber())).append(',')
                    .append(WebJson.property("dormNumber", summary.getDormNumber())).append(',')
                    .append(WebJson.numberProperty("roomCount", summary.getRoomCount())).append(',')
                    .append(WebJson.numberProperty("totalStudents", summary.getTotalStudents())).append(',')
                    .append(WebJson.numberProperty("totalCapacity", summary.getTotalCapacity())).append(',')
                    .append(WebJson.numberProperty("vacantBeds", summary.getVacantBeds())).append(',')
                    .append(WebJson.numberProperty("occupancyRate", Math.round(summary.getOccupancyRate() * 10) / 10.0))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String buildingsJson(List<Building> buildings) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < buildings.size(); i++) {
            Building building = buildings.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append(WebJson.property("buildingNumber", building.getBuildingNumber())).append(',')
                    .append(WebJson.property("buildingName", building.getBuildingName())).append(',')
                    .append(WebJson.property("genderType", building.getGenderType())).append(',')
                    .append(WebJson.numberProperty("totalFloors", building.getTotalFloors())).append(',')
                    .append(WebJson.property("status", building.getStatus()))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String roomsJson(List<DormRoom> rooms) {
        Map<String, Set<String>> occupiedBeds = new HashMap<>();
        for (StudentDormRecord student : studentDormService.listAll()) {
            occupiedBeds.computeIfAbsent(student.getDormNumber().toLowerCase(Locale.ROOT), key -> new HashSet<>())
                    .add(student.getBedNumber());
        }
        Map<String, Set<String>> lockedBeds = new HashMap<>();
        for (DormChangeRequest request : changeRequestService.listPending()) {
            lockedBeds.computeIfAbsent(request.getTargetDormNumber().toLowerCase(Locale.ROOT), key -> new HashSet<>())
                    .add(request.getTargetBedNumber());
        }

        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < rooms.size(); i++) {
            DormRoom room = rooms.get(i);
            String roomKey = room.getDormNumber().toLowerCase(Locale.ROOT);
            Set<String> occupied = occupiedBeds.getOrDefault(roomKey, Set.of());
            Set<String> locked = new HashSet<>(lockedBeds.getOrDefault(roomKey, Set.of()));
            locked.removeAll(occupied);
            List<String> vacantBedNumbers = room.isActive()
                    ? infrastructureService.activeBedNumbers(room.getDormNumber()).stream()
                            .filter(bedNumber -> !occupied.contains(bedNumber) && !locked.contains(bedNumber))
                            .collect(Collectors.toList())
                    : List.of();
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append(WebJson.property("dormNumber", room.getDormNumber())).append(',')
                    .append(WebJson.property("buildingNumber", room.getBuildingNumber())).append(',')
                    .append(WebJson.numberProperty("floorNumber", room.getFloorNumber())).append(',')
                    .append(WebJson.property("roomType", room.getRoomType())).append(',')
                    .append(WebJson.property("genderType", room.getGenderType())).append(',')
                    .append(WebJson.numberProperty("capacity", room.getCapacity())).append(',')
                    .append(WebJson.numberProperty("occupiedBeds", occupied.size())).append(',')
                    .append(WebJson.numberProperty("lockedBeds", locked.size())).append(',')
                    .append(WebJson.numberProperty("vacantBeds", vacantBedNumbers.size())).append(',')
                    .append("\"vacantBedNumbers\":").append(WebJson.stringArray(vacantBedNumbers)).append(',')
                    .append(WebJson.property("phone", room.getPhone())).append(',')
                    .append(WebJson.property("status", room.getStatus()))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String usersJson(List<User> users) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append(WebJson.property("username", user.getUsername())).append(',')
                    .append(WebJson.property("role", user.getRole().name())).append(',')
                    .append(WebJson.property("studentId", user.getStudentId())).append(',')
                    .append(WebJson.booleanProperty("enabled", user.isEnabled()))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String logsJson(List<OperationLog> logs) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < logs.size(); i++) {
            OperationLog log = logs.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append(WebJson.numberProperty("id", log.getId())).append(',')
                    .append(WebJson.property("operator", log.getOperator())).append(',')
                    .append(WebJson.property("action", log.getAction())).append(',')
                    .append(WebJson.property("targetType", log.getTargetType())).append(',')
                    .append(WebJson.property("targetId", log.getTargetId())).append(',')
                    .append(WebJson.property("detail", log.getDetail())).append(',')
                    .append(WebJson.property("createdAt", log.getCreatedAt() == null ? "" : log.getCreatedAt().format(DATE_TIME_FORMATTER)))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String modelConfigJson(ModelConfigStatus status) {
        ModelServiceConfig config = status.getEffectiveConfig();
        return "{"
                + WebJson.booleanProperty("success", true) + ","
                + WebJson.booleanProperty("configured", config.isConfigured()) + ","
                + WebJson.booleanProperty("apiKeySet", config.hasApiKey()) + ","
                + WebJson.booleanProperty("localConfigPresent", status.isLocalConfigPresent()) + ","
                + WebJson.booleanProperty("localConfigured", status.getProfiles().stream().anyMatch(ModelConfigProfile::isConfigured)) + ","
                + WebJson.booleanProperty("localAnalysis", status.isLocalAnalysisSelected()) + ","
                + WebJson.booleanProperty("environmentConfigured", status.isEnvironmentConfigured()) + ","
                + WebJson.property("activeId", status.getActiveId()) + ","
                + WebJson.property("apiUrl", config.getApiUrl()) + ","
                + WebJson.property("model", config.getModel()) + ","
                + WebJson.property("apiKeyMasked", config.maskedApiKey()) + ","
                + WebJson.property("source", config.getSource()) + ","
                + WebJson.property("sourceText", modelConfigSourceText(status, config)) + ","
                + "\"configs\":" + modelProfilesJson(status)
                + "}";
    }

    private String modelProfilesJson(ModelConfigStatus status) {
        StringBuilder builder = new StringBuilder("[");
        List<ModelConfigProfile> profiles = status.getProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            ModelConfigProfile profile = profiles.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append(WebJson.property("id", profile.getId())).append(',')
                    .append(WebJson.property("name", profile.getName())).append(',')
                    .append(WebJson.property("apiUrl", profile.getApiUrl())).append(',')
                    .append(WebJson.property("model", profile.getModel())).append(',')
                    .append(WebJson.booleanProperty("apiKeySet", profile.hasApiKey())).append(',')
                    .append(WebJson.property("apiKeyMasked", profile.maskedApiKey())).append(',')
                    .append(WebJson.booleanProperty("configured", profile.isConfigured())).append(',')
                    .append(WebJson.booleanProperty("active", !status.isLocalAnalysisSelected() && profile.getId().equals(status.getActiveId())))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String modelConfigSourceText(ModelConfigStatus status, ModelServiceConfig config) {
        if (status.isLocalAnalysisSelected()) {
            return "本地规则分析";
        }
        if ("environment".equals(config.getSource())) {
            return "环境变量";
        }
        if ("local_file".equals(config.getSource())) {
            return "本地配置文件";
        }
        return "本地规则分析";
    }

    private String pageJson(PageResult<?> page) {
        return WebJson.numberProperty("total", page.getTotal()) + ","
                + WebJson.numberProperty("page", page.getPage()) + ","
                + WebJson.numberProperty("pageSize", page.getPageSize());
    }

    private User requireUser(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-Auth-Token");
        Session session = token == null ? null : sessions.get(token);
        if (session == null) {
            throw new ApiException(401, "请先登录。");
        }
        if (session.expiresAt.isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            throw new ApiException(401, "登录已过期，请重新登录。");
        }
        User currentUser = userService.findByUsername(session.user.getUsername())
                .orElseThrow(() -> {
                    sessions.remove(token);
                    return new ApiException(401, "账号已删除，请重新登录。");
                });
        if (!currentUser.isEnabled()) {
            sessions.remove(token);
            throw new ApiException(401, "账号已停用，请联系管理员。");
        }
        session.expiresAt = LocalDateTime.now().plusMinutes(SESSION_TTL_MINUTES);
        return currentUser;
    }

    private void invalidateSessions(String username, String... excludedUsernames) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) {
            return;
        }
        sessions.entrySet().removeIf(entry -> {
            String sessionUsername = entry.getValue().user.getUsername();
            if (!sessionUsername.equalsIgnoreCase(normalized)) {
                return false;
            }
            for (String excluded : excludedUsernames) {
                if (sessionUsername.equalsIgnoreCase(excluded == null ? "" : excluded.trim())) {
                    return false;
                }
            }
            return true;
        });
    }

    private User requireAdmin(HttpExchange exchange) {
        User user = requireUser(exchange);
        if (user.getRole() != UserRole.ADMIN) {
            throw new ApiException(403, "当前账号没有管理员权限。");
        }
        return user;
    }

    private String requireBoundStudent(User user) {
        if (!user.isBoundToStudent()) {
            throw new ApiException(403, "当前普通用户未绑定学号，不能查看或提交调换申请。");
        }
        return user.getStudentId();
    }

    private <T> PageResult<T> paginate(List<T> items, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), 100);
        int from = Math.min((safePage - 1) * safePageSize, items.size());
        int to = Math.min(from + safePageSize, items.size());
        return new PageResult<>(items.subList(from, to), items.size(), safePage, safePageSize);
    }

    private boolean parseBoolean(String value) {
        return value == null || value.isBlank() || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "1".equals(value);
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String loginKey(HttpExchange exchange, String username) {
        String address = exchange.getRemoteAddress() == null ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
        return address + ":" + username.toLowerCase();
    }

    private boolean isLoginRateLimited(String key) {
        LoginAttempt attempt = loginAttempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (System.currentTimeMillis() - attempt.windowStartedAt > LOGIN_WINDOW_MILLIS) {
            loginAttempts.remove(key);
            return false;
        }
        return attempt.failures >= MAX_LOGIN_FAILURES;
    }

    private void recordLoginFailure(String key) {
        loginAttempts.compute(key, (ignored, attempt) -> {
            long now = System.currentTimeMillis();
            if (attempt == null || now - attempt.windowStartedAt > LOGIN_WINDOW_MILLIS) {
                return new LoginAttempt(1, now);
            }
            attempt.failures++;
            return attempt;
        });
    }

    private void cleanupExpiredSecurityState() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
        long currentMillis = System.currentTimeMillis();
        loginAttempts.entrySet().removeIf(entry ->
                currentMillis - entry.getValue().windowStartedAt > LOGIN_WINDOW_MILLIS);
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int index = pair.indexOf('=');
            String key = index < 0 ? pair : pair.substring(0, index);
            String value = index < 0 ? "" : pair.substring(index + 1);
            values.put(urlDecode(key), urlDecode(value));
        }
        return values;
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        String requested = "/".equals(path) ? "/index.html" : path;
        Path target = webRoot.resolve(requested.substring(1)).normalize();
        if (!target.startsWith(webRoot) || !Files.exists(target) || Files.isDirectory(target)) {
            sendText(exchange, 404, "Not Found", "text/plain; charset=utf-8");
            return;
        }
        sendBytes(exchange, 200, Files.readAllBytes(target), contentType(target));
    }

    private String contentType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendText(exchange, statusCode, json, "application/json; charset=utf-8");
    }

    private void sendText(HttpExchange exchange, int statusCode, String text, String contentType) throws IOException {
        sendBytes(exchange, statusCode, text.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private void sendBytes(HttpExchange exchange, int statusCode, byte[] bytes, String contentType) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static class Session {
        private final User user;
        private LocalDateTime expiresAt;

        private Session(User user, LocalDateTime expiresAt) {
            this.user = user;
            this.expiresAt = expiresAt;
        }
    }

    private static class LoginAttempt {
        private int failures;
        private final long windowStartedAt;

        private LoginAttempt(int failures, long windowStartedAt) {
            this.failures = failures;
            this.windowStartedAt = windowStartedAt;
        }
    }

    private static class ApiException extends RuntimeException {
        private final int statusCode;

        private ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
