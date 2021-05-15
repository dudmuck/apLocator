package com.example.aplocator;

import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RoomDatabase;

import java.util.List;

@Dao
public interface AzimuthDao {
    @Query("SELECT * FROM azimuth")
    List<Azimuth> getAll();

    @Query("SELECT * FROM azimuth WHERE uid IN (:userIds)")
    List<Azimuth> loadAllByIds(int[] userIds);

    @Query("SELECT * FROM azimuth WHERE bssid LIKE :mac_addr AND " +
            "bearing LIKE :deg LIMIT 1")
    Azimuth findByBSSID_bearing(String mac_addr, Integer deg);

    @Query("SELECT * FROM azimuth WHERE bssid LIKE :mac_addr")
    List<Azimuth> findByBSSID(String mac_addr);

    @Query("SELECT DISTINCT bssid FROM azimuth")
    List<String> get_BSSIDs();

    @Query("DELETE FROM azimuth WHERE bssid = :macAddress AND bearing = :degrees")
    int deleteWithBearing(String macAddress, int degrees);

    @Query("UPDATE azimuth SET latitude = :lat, longitude = :lon WHERE bssid = :macAddress AND bearing < 0")
    int update_ap_location(String macAddress, Double lat, Double lon);

    // onConflict = OnConflictStrategy.IGNORE
    @Insert()
    void insert(Azimuth az);

    @Insert
    void insertAll(Azimuth... users);

    @Delete
    void delete(Azimuth user);
}
