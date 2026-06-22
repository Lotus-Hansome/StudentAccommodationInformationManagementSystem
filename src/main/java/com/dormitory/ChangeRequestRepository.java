package com.dormitory;

import java.io.IOException;
import java.util.List;

public interface ChangeRequestRepository {
    List<DormChangeRequest> load() throws IOException;

    void save(List<DormChangeRequest> requests) throws IOException;
}
