package com.dormitory;

import java.io.IOException;
import java.util.List;

public interface DormInfrastructureRepository {
    List<Building> loadBuildings() throws IOException;

    List<DormRoom> loadRooms() throws IOException;

    List<DormBed> loadBeds() throws IOException;

    void upsertBuilding(Building building) throws IOException;

    void upsertRoom(DormRoom room) throws IOException;
}
