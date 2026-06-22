package com.dormitory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CsvStudentRepository implements StudentRepository {
    private final Path dataFile;
    private final boolean seedIfMissing;

    public CsvStudentRepository(Path dataDirectory) {
        this(dataDirectory, true);
    }

    public CsvStudentRepository(Path dataDirectory, boolean seedIfMissing) {
        this.dataFile = dataDirectory.resolve("students.csv");
        this.seedIfMissing = seedIfMissing;
    }

    @Override
    public List<StudentDormRecord> load() throws IOException {
        ensureFile();
        List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
        List<StudentDormRecord> records = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            List<String> fields = CsvUtil.parseLine(line);
            if (fields.size() < 7) {
                continue;
            }
            records.add(new StudentDormRecord(
                    fields.get(0),
                    fields.get(1),
                    fields.get(2),
                    fields.get(3),
                    fields.get(4),
                    fields.get(5),
                    fields.get(6)));
        }
        return records;
    }

    @Override
    public void save(List<StudentDormRecord> records) throws IOException {
        Files.createDirectories(dataFile.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("studentId,name,department,className,dormNumber,dormPhone,bedNumber");
        for (StudentDormRecord record : records) {
            lines.add(CsvUtil.toLine(List.of(
                    record.getStudentId(),
                    record.getName(),
                    record.getDepartment(),
                    record.getClassName(),
                    record.getDormNumber(),
                    record.getDormPhone(),
                    record.getBedNumber())));
        }
        Files.write(dataFile, lines, StandardCharsets.UTF_8);
    }

    private void ensureFile() throws IOException {
        Files.createDirectories(dataFile.getParent());
        if (Files.exists(dataFile)) {
            return;
        }
        if (seedIfMissing) {
            save(seedRecords());
            return;
        }
        Files.write(dataFile, List.of("studentId,name,department,className,dormNumber,dormPhone,bedNumber"), StandardCharsets.UTF_8);
    }

    private List<StudentDormRecord> seedRecords() {
        return List.of(
                new StudentDormRecord("20230001", "张明", "计算机系", "软件2301", "3-501", "0571-3501", "1"),
                new StudentDormRecord("20230002", "李华", "计算机系", "软件2301", "3-501", "0571-3501", "2"),
                new StudentDormRecord("20230003", "王芳", "信息工程系", "物联2302", "3-502", "0571-3502", "1"),
                new StudentDormRecord("20230004", "赵强", "机电工程系", "机电2301", "2-401", "0571-2401", "3"),
                new StudentDormRecord("20230005", "陈晨", "外语系", "英语2301", "2-402", "0571-2402", "2"),
                new StudentDormRecord("20230006", "刘洋", "计算机系", "网络2301", "3-502", "0571-3502", "2"));
    }
}
