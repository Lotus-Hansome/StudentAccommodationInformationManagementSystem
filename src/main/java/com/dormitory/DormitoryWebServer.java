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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class DormitoryWebServer {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthService authService;
    private final StudentDormService studentDormService;
    private final ChangeRequestService changeRequestService;
    private final DormAnalysisService dormAnalysisService;
    private final ModelConfigService modelConfigService;
    private final Map<String, User> sessions;
    private final Path webRoot;
    private final int port;

    public DormitoryWebServer(int port, Path webRoot) {
        MysqlConnectionFactory connectionFactory = MysqlSupport.initializedConnectionFactory();
        this.authService = new AuthService();
        this.studentDormService = new StudentDormService(new MysqlStudentRepository(connectionFactory));
        this.changeRequestService = new ChangeRequestService(new MysqlChangeRequestRepository(connectionFactory), studentDormService);
        this.dormAnalysisService = new DormAnalysisService();
        this.modelConfigService = new ModelConfigService();
        this.sessions = new ConcurrentHashMap<>();
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
            System.out.println("数据库：MySQL，账号默认 admin/admin123、student/student123");
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
        if ("/api/statistics".equals(path) && "GET".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            statistics(exchange);
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
        Optional<User> user = authService.login(form.getOrDefault("username", ""), form.getOrDefault("password", ""));
        if (user.isEmpty()) {
            throw new ApiException(401, "用户名或密码错误。");
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, user.get());
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
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String mode = query.getOrDefault("mode", "all");
            List<StudentDormRecord> records;
            if ("student".equals(mode)) {
                records = studentDormService.findByStudentId(query.getOrDefault("studentId", "")).map(List::of).orElseGet(List::of);
            } else if ("dorm".equals(mode)) {
                records = studentDormService.findByDormNumber(query.getOrDefault("dormNumber", ""));
            } else if ("departmentClass".equals(mode)) {
                records = studentDormService.findByDepartmentAndClass(
                        query.getOrDefault("department", ""),
                        query.getOrDefault("className", ""));
            } else if ("sorted".equals(mode)) {
                records = studentDormService.sortByDepartmentAndClass();
            } else {
                records = studentDormService.listAll();
            }
            sendJson(exchange, 200, "{\"success\":true,\"students\":" + studentsJson(records, user.getRole() == UserRole.ADMIN) + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            Map<String, String> form = readForm(exchange);
            studentDormService.add(studentFromForm(form));
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"添加成功。\"}");
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            Map<String, String> form = readForm(exchange);
            studentDormService.update(studentFromForm(form));
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"修改成功。\"}");
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            requireAdmin(exchange);
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            boolean removed = studentDormService.deleteByDormAndStudent(query.getOrDefault("dormNumber", ""), query.getOrDefault("studentId", ""));
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
            sendJson(exchange, 200, "{\"success\":true,\"requests\":" + requestsJson(requests) + "}");
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
            sendJson(exchange, 200, "{\"success\":true,\"id\":" + WebJson.quote(request.getId()) + "}");
            return;
        }
        throw new ApiException(405, "请求方法不支持。");
    }

    private void requestDecision(HttpExchange exchange, boolean approve) throws IOException {
        Map<String, String> form = readForm(exchange);
        String requestId = form.getOrDefault("requestId", "");
        String comment = form.getOrDefault("comment", "");
        if (approve) {
            changeRequestService.approve(requestId, comment);
        } else {
            changeRequestService.reject(requestId, comment);
        }
        sendJson(exchange, 200, "{\"success\":true,\"message\":\"审批完成。\"}");
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

    private User requireUser(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-Auth-Token");
        if (token == null || !sessions.containsKey(token)) {
            throw new ApiException(401, "请先登录。");
        }
        return sessions.get(token);
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

    private static class ApiException extends RuntimeException {
        private final int statusCode;

        private ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
