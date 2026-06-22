package com.dormitory;

import java.io.IOException;
import java.util.List;

public interface StudentRepository {
    List<StudentDormRecord> load() throws IOException;

    void save(List<StudentDormRecord> records) throws IOException;
}
