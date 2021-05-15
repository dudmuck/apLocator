package com.example.aplocator;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Azimuth {
    @PrimaryKey(autoGenerate = true)
    public int uid;

    @ColumnInfo(name = "latitude")
    public Double lat;

    @ColumnInfo(name = "longitude")
    public Double lon;

    @ColumnInfo(name = "bssid")
    public String macAddress;

    @ColumnInfo(name = "bearing")
    public int degrees;
}