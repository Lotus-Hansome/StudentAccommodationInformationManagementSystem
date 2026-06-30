package com.dormitory;

import java.io.IOException;
import java.util.List;

public interface StudentRepository {
    List<StudentDormRecord> load() throws IOException;

    void save(List<StudentDormRecord> records) throws IOException;

    default void insert(StudentDormRecord record) throws IOException {
        List<StudentDormRecord> records = new java.util.ArrayList<>(load());
        records.add(record);
        save(records);
    }

    default void update(StudentDormRecord record) throws IOException {
        List<StudentDormRecord> records = new java.util.ArrayList<>(load());
        boolean replaced = false;
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).getStudentId().equalsIgnoreCase(record.getStudentId())) {
                records.set(i, record);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            throw new IOException("未找到要修改的学生住宿记录。");
        }
        save(records);
    }

    default boolean deleteByDormAndStudent(String dormNumber, String studentId) throws IOException {
        List<StudentDormRecord> records = new java.util.ArrayList<>(load());
        boolean removed = records.removeIf(record ->
                record.getDormNumber().equalsIgnoreCase(dormNumber)
                        && record.getStudentId().equalsIgnoreCase(studentId));
        if (removed) {
            save(records);
        }
        return removed;
    }
}
