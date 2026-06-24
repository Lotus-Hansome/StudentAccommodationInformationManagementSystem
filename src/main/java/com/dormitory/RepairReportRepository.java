package com.dormitory;

import java.io.IOException;
import java.util.List;

public interface RepairReportRepository {
    List<RepairReport> load() throws IOException;

    void save(List<RepairReport> reports) throws IOException;
}
