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
        String normalizedStudentId = normalizeText(studentId);
        String normalizedTargetDormNumber = normalizeText(targetDormNumber);
        String normalizedTargetDormPhone = normalizeText(targetDormPhone);
        String normalizedTargetBedNumber = normalizeText(targetBedNumber);
        String normalizedReason = normalizeText(reason);
        if (normalizedTargetDormNumber.isBlank()
                || normalizedTargetDormPhone.isBlank()
                || normalizedTargetBedNumber.isBlank()
                || normalizedReason.isBlank()) {
            throw new IllegalArgumentException("目标宿舍、目标电话、目标床位和调换理由不能为空。");
        }

        StudentDormRecord student = studentDormService.findByStudentId(normalizedStudentId)
                .orElseThrow(() -> new IllegalArgumentException("学号不存在，不能提交调换申请。"));
        boolean hasPendingRequest = requests.stream()
                .anyMatch(request -> request.getStudentId().equalsIgnoreCase(normalizedStudentId)
                        && request.getStatus() == ChangeRequestStatus.PENDING);
        if (hasPendingRequest) {
            throw new IllegalArgumentException("该学生已有待审核调换申请，请勿重复提交。");
        }
        boolean targetLocked = requests.stream()
                .anyMatch(request -> request.getStatus() == ChangeRequestStatus.PENDING
                        && request.getTargetDormNumber().equalsIgnoreCase(normalizedTargetDormNumber)
                        && request.getTargetBedNumber().equalsIgnoreCase(normalizedTargetBedNumber));
        if (targetLocked) {
            throw new IllegalArgumentException("目标床位已有待审核调换申请，暂时不能重复申请。");
        }
        if (student.isSameBed(normalizedTargetDormNumber, normalizedTargetBedNumber)) {
            throw new IllegalArgumentException("目标床位与当前床位相同，无需提交调换申请。");
        }
        if (!studentDormService.isBedAvailable(normalizedTargetDormNumber, normalizedTargetBedNumber, normalizedStudentId)) {
            throw new IllegalArgumentException("目标宿舍床位已被占用，请重新选择。");
        }

        String id = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        DormChangeRequest request = new DormChangeRequest(
                id,
                normalizedStudentId,
                student.getDormNumber(),
                student.getBedNumber(),
                normalizedTargetDormNumber,
                normalizedTargetDormPhone,
                normalizedTargetBedNumber,
                normalizedReason,
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
        String normalizedStudentId = normalizeText(studentId);
        return requests.stream()
                .filter(request -> request.getStudentId().equalsIgnoreCase(normalizedStudentId))
                .sorted(Comparator.comparing(DormChangeRequest::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public void approve(String requestId, String adminComment) {
        String normalizedComment = normalizeText(adminComment);
        if (normalizedComment.isBlank()) {
            throw new IllegalArgumentException("审批意见不能为空。");
        }
        DormChangeRequest request = findPending(requestId)
                .orElseThrow(() -> new IllegalArgumentException("未找到待审核申请。"));
        StudentDormRecord student = studentDormService.findByStudentId(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("申请学生已不存在，无法审批通过。"));
        if (!student.isSameBed(request.getCurrentDormNumber(), request.getCurrentBedNumber())) {
            throw new IllegalArgumentException("学生当前床位已变化，该调换申请已过期。");
        }
        studentDormService.updateDorm(
                request.getStudentId(),
                request.getTargetDormNumber(),
                request.getTargetDormPhone(),
                request.getTargetBedNumber());
        request.setStatus(ChangeRequestStatus.APPROVED);
        request.setHandledAt(LocalDateTime.now());
        request.setAdminComment(normalizedComment);
        rejectOtherPendingRequests(request);
        save();
    }

    public void reject(String requestId, String adminComment) {
        String normalizedComment = normalizeText(adminComment);
        if (normalizedComment.isBlank()) {
            throw new IllegalArgumentException("审批意见不能为空。");
        }
        DormChangeRequest request = findPending(requestId)
                .orElseThrow(() -> new IllegalArgumentException("未找到待审核申请。"));
        request.setStatus(ChangeRequestStatus.REJECTED);
        request.setHandledAt(LocalDateTime.now());
        request.setAdminComment(normalizedComment);
        save();
    }

    public void cancel(String requestId, String studentId) {
        String normalizedRequestId = normalizeText(requestId);
        String normalizedStudentId = normalizeText(studentId);
        DormChangeRequest request = requests.stream()
                .filter(item -> item.getId().equalsIgnoreCase(normalizedRequestId))
                .filter(item -> item.getStudentId().equalsIgnoreCase(normalizedStudentId))
                .filter(item -> item.getStatus() == ChangeRequestStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到可撤回的待审核申请。"));
        request.setStatus(ChangeRequestStatus.CANCELED);
        request.setHandledAt(LocalDateTime.now());
        request.setAdminComment("学生主动撤回。");
        save();
    }

    private Optional<DormChangeRequest> findPending(String requestId) {
        String normalizedRequestId = normalizeText(requestId);
        return requests.stream()
                .filter(request -> request.getId().equalsIgnoreCase(normalizedRequestId))
                .filter(request -> request.getStatus() == ChangeRequestStatus.PENDING)
                .findFirst();
    }

    private void rejectOtherPendingRequests(DormChangeRequest approvedRequest) {
        for (DormChangeRequest request : requests) {
            if (!request.getId().equals(approvedRequest.getId())
                    && request.getStudentId().equalsIgnoreCase(approvedRequest.getStudentId())
                    && request.getStatus() == ChangeRequestStatus.PENDING) {
                request.setStatus(ChangeRequestStatus.REJECTED);
                request.setHandledAt(LocalDateTime.now());
                request.setAdminComment("已有其他调换申请通过，系统自动关闭该申请。");
            }
        }
    }

    private void save() {
        try {
            repository.save(requests);
        } catch (IOException e) {
            throw new IllegalStateException("保存调换申请失败：" + e.getMessage(), e);
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
