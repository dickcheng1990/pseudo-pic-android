package com.example.pseudo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pseudo.PseudoApp
import com.example.pseudo.database.AppDatabase
import com.example.pseudo.database.ImageRecord
import com.example.pseudo.models.ProcessingResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase = (application as PseudoApp).database
    val historyFlow: Flow<List<ImageRecord>> = database.imageDao().getAll()

    fun saveProcessingResults(results: List<ProcessingResult>) {
        viewModelScope.launch {
            val records = results.mapNotNull { r ->
                if (r.success) ImageRecord(
                    originalPath = r.inputPath,
                    outputPath = r.outputPath ?: "",
                    originalFilename = java.io.File(r.inputPath).name,
                    timestamp = System.currentTimeMillis(),
                    width = 0,
                    height = 0,
                    fileSize = r.outputPath?.let { java.io.File(it).length() } ?: 0L,
                    originalHash = r.originalHash,
                    processedHash = r.processedHash,
                    mode = "standard"
                ) else null
            }
            database.imageDao().insertAll(records)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            database.imageDao().deleteAll()
        }
    }
}
