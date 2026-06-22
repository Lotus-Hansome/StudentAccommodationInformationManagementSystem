package com.dormitory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ChangeRequestService {
    private final ChangeRequestRepository repository;
    private final StudentDormService studentDormService;
    private final List<DormChangeRequest> requests;

    public ChangeRequestService(ChangeRequestRepository repository, StudentDormService studentDormService) {
        this.repository = repository;
        this.studentDormService = studentDormService;
        try {
            this.requests = new ArrayList<>(repository.load());
        } catch (IOException e) {
            throw new IllegalStateException("读取宿舍调换申请失败：" + e.getMessage(), e);
        }
    }

    public DormChangeRequest submit(String studentId, String targetDormNumber, String targetDormPhone, String targetBedNumber, String reason) {
        StudentDormRecord student = studentDormService.findByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("学号不存在，不能提交调换申请。"));
        if (!studentDormService.isBedAvailable(targetDormNumber, targetBedNumber, studentId)) {
            throw new IllegalArgumentException("目标宿舍床位已被占用，请重新选择。");
        }
        String id = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        DormChangeRequest request = new DormChangeRequest(
                id,
                studentId,
                student.getDormNumber(),
                student.getBedNumber(),
                targetDormNumber,
                targetDormPhone,
                targetBedNumber,
                reason,
                ChangeRequestStatus.PENDING,
                LocalDateTime.now(),
                null,
                "");
        requests.add(request);
        save();
        return request;
    }

    public List<DormChangeRequest> listAll() {
        return requests.stream()
                .sorted(Comparator.comparing(DormChangeRequest::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<DormChangeRequest> listPending() {
        return requests.stream()
                .filter(request -> request.getStatus() == ChangeRequestStatus.PENDING)
                .sorted(Comparator.comparing(DormChangeRequest::getCreatedAt))
                .collect(Collectors.toList());
    }

    public List<DormChangeRequest> listByStudentId(String studentId) {
        return requests.stream()
                .filter(request -> request.getStudentId().equalsIgnoreCase(studentId))
                .sorted(Comparator.comparing(DormChangeRequest::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public void approve(String requestId, String adminComment) {
        DormChangeRequest request = findPending(requestId)
                .orElseThrow(() -> new IllegalArgumentException("未找到待审核申请。"));
        studentDormService.updateDorm(
                request.getStudentId(),
                request.getTargetDormNumber(),
                request.getTargetDormPhone(),
                request.getTargetBedNumber());
        request.setStatus(ChangeRequestStatus.APPROVED);
        request.setHandledAt(LocalDateTime.now());
        request.setAdminComment(adminComment);
        save();
    }

    public void reject(String requestId, String adminComment) {
        DormChangeRequest request = findPending(requestId)
                .orElseThrow(() -> new IllegalArgumentException("未找到待审核申请。"));
        request.setStatus(ChangeRequestStatus.REJECTED);
        request.setHandledAt(LocalDateTime.now());
        request.setAdminComment(adminComment);
        save();
    }

    private Optional<DormChangeRequest> findPending(String requestId) {
        return requests.stream()
                .filter(request -> request.getId().equalsIgnoreCase(requestId))
                .filter(request -> request.getStatus() == ChangeRequestStatus.PENDING)
                .findFirst();
    }

    private void save() {
        try {
            repository.save(requests);
        } catch (IOException e) {
            throw new IllegalStateException("保存调换申请失败：" + e.getMessage(), e);
        }
    }
}
