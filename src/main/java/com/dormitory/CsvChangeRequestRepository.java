package com.dormitory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CsvChangeRequestRepository implements ChangeRequestRepository {
    private final Path dataFile;

    public CsvChangeRequestRepository(Path dataDirectory) {
        this.dataFile = dataDirectory.resolve("change_requests.csv");
    }

    @Override
    public List<DormChangeRequest> load() throws IOException {
        ensureFile();
        List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
        List<DormChangeRequest> requests = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            List<String> fields = CsvUtil.parseLine(line);
            if (fields.size() < 12) {
                continue;
            }
            requests.add(new DormChangeRequest(
                    fields.get(0),
                    fields.get(1),
                    fields.get(2),
                    fields.get(3),
                    fields.get(4),
                    fields.get(5),
                    fields.get(6),
                    fields.get(7),
                    ChangeRequestStatus.fromName(fields.get(8)),
                    parseDateTime(fields.get(9)),
                    parseDateTime(fields.get(10)),
                    fields.get(11)));
        }
        return requests;
    }

    @Override
    public void save(List<DormChangeRequest> requests) throws IOException {
        Files.createDirectories(dataFile.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("id,studentId,currentDormNumber,currentBedNumber,targetDormNumber,targetDormPhone,targetBedNumber,reason,status,createdAt,handledAt,adminComment");
        for (DormChangeRequest request : requests) {
            lines.add(CsvUtil.toLine(List.of(
                    request.getId(),
                    request.getStudentId(),
                    request.getCurrentDormNumber(),
                    request.getCurrentBedNumber(),
                    request.getTargetDormNumber(),
                    request.getTargetDormPhone(),
                    request.getTargetBedNumber(),
                    request.getReason(),
                    request.getStatus().name(),
                    formatDateTime(request.getCreatedAt()),
                    formatDateTime(request.getHandledAt()),
                    request.getAdminComment())));
        }
        Files.write(dataFile, lines, StandardCharsets.UTF_8);
    }

    private void ensureFile() throws IOException {
        Files.createDirectories(dataFile.getParent());
        if (!Files.exists(dataFile)) {
            Files.write(
                    dataFile,
                    List.of("id,studentId,currentDormNumber,currentBedNumber,targetDormNumber,targetDormPhone,targetBedNumber,reason,status,createdAt,handledAt,adminComment"),
                    StandardCharsets.UTF_8);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
