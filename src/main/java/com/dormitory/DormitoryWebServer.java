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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        this.authService = new AuthService(userRepository);
        this.userService = new UserService(userRepository);
        this.infrastructureService = new DormInfrastructureService(new MysqlDormInfrastructureRepository(connectionFactory));
        this.studentDormService = new StudentDormService(new MysqlStudentRepository(connectionFactory), infrastructureService);
        this.changeRequestService = new ChangeRequestService(new MysqlChangeRequestRepository(connectionFactory), studentDormService);
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
            sendJson(exchange, 500, "{\"success\":false,\"message\":" + WebJson.quote(e.getMessage()) + "}");
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
            requireUser(exchange);
            overview(exchange);
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
            requireAdmin(exchange);
            modelConfig(exchange, method);
            return;
        }
        throw new ApiException(404, "接口不存在。");
    }

    private void login(HttpExchange exchange) throws IOException {
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
            throw new ApiException(401, "用户名或密码错误，或账号已被禁用。");
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
            studentDormService.add(record);
            operationLogService.record(admin.getUsername(), "ADD_STUDENT", "student", record.getStudentId(), "新增学生住宿信息");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"添加成功。\"}");
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            User admin = requireAdmin(exchange);
            Map<String, String> form = readForm(exchange);
            StudentDormRecord record = studentFromForm(form);
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
            operationLogService.record(admin.getUsername(), "DELETE_STUDENT", "student", studentId, "删除宿舍 " + dormNumber + " 的住宿信息");
            sendJson(exchange, 200, "{\"success\":true,\"removed\":" + removed + "}");
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
        sendJson(exchange, 200, "{"
                + WebJson.booleanProperty("success", true) + ","
                + WebJson.property("scope", scope) + ","
                + "\"items\":" + occupancyJson(summaries)
                + "}");
    }

    private void buildings(HttpExchange exchange, String method) throws IOException {
        User admin = requireAdmin(exchange);
        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            sendJson(exchange, 200, "{\"success\":true,\"buildings\":" + buildingsJson(infrastructureService.listBuildings(query.getOrDefault("keyword", ""))) + "}");
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
            sendJson(exchange, 200, "{\"success\":true,\"rooms\":" + roomsJson(infrastructureService.listRooms(
                    query.getOrDefault("buildingNumber", ""),
                    query.getOrDefault("keyword", ""))) + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            DormRoom room = roomFromForm(readForm(exchange));
            infrastructureService.saveRoom(room);
            operationLogService.record(admin.getUsername(), "SAVE_ROOM", "dorm_room", room.getDormNumber(), "保存宿舍基础信息");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"宿舍已保存。\"}");
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private void users(HttpExchange exchange, String method) throws IOException {
        User admin = requireAdmin(exchange);
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, "{\"success\":true,\"users\":" + usersJson(userService.listAll()) + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            Map<String, String> form = readForm(exchange);
            userService.create(
                    form.getOrDefault("username", ""),
                    form.getOrDefault("password", ""),
                    form.getOrDefault("role", "USER"),
                    form.getOrDefault("studentId", ""),
                    parseBoolean(form.getOrDefault("enabled", "true")));
            operationLogService.record(admin.getUsername(), "ADD_USER", "user", form.getOrDefault("username", ""), "新增系统账号");
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
            operationLogService.record(admin.getUsername(), "UPDATE_USER", "user", form.getOrDefault("username", ""), "更新系统账号");
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"用户已更新。\"}");
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private void changePassword(HttpExchange exchange) throws IOException {
        User user = requireUser(exchange);
        Map<String, String> form = readForm(exchange);
        userService.changePassword(user.getUsername(), form.getOrDefault("oldPassword", ""), form.getOrDefault("newPassword", ""));
        operationLogService.record(user.getUsername(), "CHANGE_PASSWORD", "user", user.getUsername(), "修改本人密码");
        sendJson(exchange, 200, "{\"success\":true,\"message\":\"密码已修改，请重新登录。\"}");
    }

    private void resetPassword(HttpExchange exchange) throws IOException {
        User admin = requireAdmin(exchange);
        Map<String, String> form = readForm(exchange);
        String username = form.getOrDefault("username", "");
        userService.resetPassword(username, form.getOrDefault("newPassword", ""));
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

    private void modelConfig(HttpExchange exchange, String method) throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, modelConfigJson(modelConfigService.loadStatusConfig()));
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            Map<String, String> form = readForm(exchange);
            ModelServiceConfig config = modelConfigService.saveLocalConfig(
                    form.getOrDefault("apiUrl", ""),
                    form.getOrDefault("apiKey", ""),
                    form.getOrDefault("model", ""));
            sendJson(exchange, 200, modelConfigJson(config));
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
                form.getOrDefault("roomType", "标准六人间"),
                form.getOrDefault("genderType", "MIXED"),
                parseInt(form.get("capacity"), 6),
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
            builder.append('{')
                    .append(WebJson.property("studentId", record.getStudentId())).append(',')
                    .append(WebJson.property("name", record.getName())).append(',')
                    .append(WebJson.property("department", record.getDepartment())).append(',')
                    .append(WebJson.property("className", record.getClassName())).append(',')
                    .append(WebJson.property("dormNumber", record.getDormNumber())).append(',')
                    .append(WebJson.property("dormPhone", record.getDormPhone())).append(',')
                    .append(WebJson.property("bedNumber", record.getBedNumber())).append(',')
                    .append(WebJson.booleanProperty("editable", includeActions))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
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
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < rooms.size(); i++) {
            DormRoom room = rooms.get(i);
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

    private String modelConfigJson(ModelServiceConfig config) {
        String sourceText = "environment".equals(config.getSource()) ? "环境变量" : "本地配置文件";
        return "{"
                + WebJson.booleanProperty("success", true) + ","
                + WebJson.booleanProperty("configured", config.isConfigured()) + ","
                + WebJson.booleanProperty("apiKeySet", config.hasApiKey()) + ","
                + WebJson.property("apiUrl", config.getApiUrl()) + ","
                + WebJson.property("model", config.getModel()) + ","
                + WebJson.property("apiKeyMasked", config.maskedApiKey()) + ","
                + WebJson.property("source", config.getSource()) + ","
                + WebJson.property("sourceText", sourceText)
                + "}";
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
        session.expiresAt = LocalDateTime.now().plusMinutes(SESSION_TTL_MINUTES);
        return session.user;
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
