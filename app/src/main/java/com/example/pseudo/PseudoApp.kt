package com.example.pseudo

import android.app.Application
import com.example.pseudo.database.AppDatabase

class PseudoApp : Application() {
    lateinit var database: AppDatabase
    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
    }
}
