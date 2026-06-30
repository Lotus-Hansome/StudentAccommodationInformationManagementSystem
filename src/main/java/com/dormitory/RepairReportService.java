package com.dormitory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RepairReportService {
    private final RepairReportRepository repository;
    private final List<RepairReport> reports;

    public RepairReportService(RepairReportRepository repository) {
        this.repository = repository;
        try {
            this.reports = new ArrayList<>(repository.load());
        } catch (IOException e) {
            throw new IllegalStateException("读取报修反馈失败：" + e.getMessage(), e);
        }
    }

    public synchronized RepairReport submit(String studentId, String dormNumber, String category, String description) {
        String normalizedStudentId = normalizeText(studentId);
        String normalizedDormNumber = normalizeText(dormNumber);
        String normalizedCategory = normalizeText(category);
        String normalizedDescription = normalizeText(description);
        if (normalizedStudentId.isBlank() || normalizedDormNumber.isBlank() || normalizedCategory.isBlank() || normalizedDescription.isBlank()) {
            throw new IllegalArgumentException("报修学生、宿舍、类型和问题描述不能为空。");
        }
        String id = "R" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-"
                + UUID.randomUUID().toString().substring(0, 6);
        RepairReport report = new RepairReport(
                id,
                normalizedStudentId,
                normalizedDormNumber,
                normalizedCategory,
                normalizedDescription,
                RepairStatus.PENDING,
                LocalDateTime.now(),
                null,
                "");
        reports.add(report);
        save();
        return report;
    }

    public synchronized List<RepairReport> listAll() {
        return reports.stream()
                .sorted(Comparator.comparing(RepairReport::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public synchronized List<RepairReport> listByStudentId(String studentId) {
        String normalizedStudentId = normalizeText(studentId);
        return reports.stream()
                .filter(report -> report.getStudentId().equalsIgnoreCase(normalizedStudentId))
                .sorted(Comparator.comparing(RepairReport::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public synchronized void updateStatus(String id, String status, String adminComment) {
        String normalizedId = normalizeText(id);
        String normalizedComment = normalizeText(adminComment);
        RepairStatus repairStatus = RepairStatus.fromName(status);
        if (normalizedComment.isBlank() && repairStatus != RepairStatus.PENDING) {
            throw new IllegalArgumentException("处理意见不能为空。");
        }
        RepairReport report = reports.stream()
                .filter(item -> item.getId().equalsIgnoreCase(normalizedId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到报修反馈。"));
        report.setStatus(repairStatus);
        report.setHandledAt(repairStatus == RepairStatus.PENDING ? null : LocalDateTime.now());
        report.setAdminComment(repairStatus == RepairStatus.PENDING ? "" : normalizedComment);
        save();
    }

    public synchronized void cancel(String id, String studentId) {
        String normalizedId = normalizeText(id);
        String normalizedStudentId = normalizeText(studentId);
        RepairReport report = reports.stream()
                .filter(item -> item.getId().equalsIgnoreCase(normalizedId))
                .filter(item -> item.getStudentId().equalsIgnoreCase(normalizedStudentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到可撤回的报修反馈。"));
        if (report.getStatus() == RepairStatus.DONE
                || report.getStatus() == RepairStatus.REJECTED
                || report.getStatus() == RepairStatus.CANCELED) {
            throw new IllegalArgumentException("该报修反馈已处理结束，不能撤回。");
        }
        report.setStatus(RepairStatus.CANCELED);
        report.setHandledAt(LocalDateTime.now());
        report.setAdminComment("学生主动撤回。");
        save();
    }

    private void save() {
        try {
            repository.save(reports);
        } catch (IOException e) {
            throw new IllegalStateException("保存报修反馈失败：" + e.getMessage(), e);
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
