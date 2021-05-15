package com.example.aplocator;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Azimuth.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AzimuthDao azimuthDao();
}