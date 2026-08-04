package com.example.pseudo.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pseudo.databinding.ItemProcessingResultBinding
import com.example.pseudo.databinding.ItemHistoryBinding
import com.example.pseudo.models.ProcessingResult
import com.example.pseudo.database.ImageRecord
import java.text.SimpleDateFormat
import java.io.File
import java.util.*

class ProcessingResultAdapter(
    private val onSave: (ProcessingResult) -> Unit
) : ListAdapter<ProcessingResult, ProcessingResultAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemProcessingResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(result: ProcessingResult) {
            binding.textViewFileName.text = File(result.inputPath).name
            binding.textViewStatus.text = if (result.success) "成功" else "失败"
            binding.textViewTime.text = "${result.processingTimeMs}ms"
            binding.textViewOutputPath.text = result.outputPath ?: ""
            val c = if (result.success) 0xFF2E7D32.toInt() else 0xFFC62828.toInt()
            binding.textViewStatus.setTextColor(c)

            val hasOutput = result.success && !result.outputPath.isNullOrEmpty()
            binding.buttonSave.visibility = if (hasOutput) View.VISIBLE else View.GONE
            binding.buttonSave.setOnClickListener { onSave(result) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemProcessingResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<ProcessingResult>() {
        override fun areItemsTheSame(o: ProcessingResult, n: ProcessingResult) = o.inputPath == n.inputPath
        override fun areContentsTheSame(o: ProcessingResult, n: ProcessingResult) = o == n
    }
}

class HistoryAdapter(
    private val onSave: (ImageRecord) -> Unit
) : ListAdapter<ImageRecord, HistoryAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: ImageRecord) {
            binding.textViewFileName.text = record.originalFilename
            binding.textViewTimestamp.text = dateFormat.format(Date(record.timestamp))
            binding.textViewSize.text = when {
                record.fileSize < 1024 -> "${record.fileSize} B"
                record.fileSize < 1024 * 1024 -> "${record.fileSize / 1024} KB"
                else -> "${record.fileSize / 1024 / 1024} MB"
            }
            binding.textViewMode.text = if (record.mode == "deep") "深度" else "标准"

            val fileExists = File(record.outputPath).exists()
            binding.buttonSave.visibility = if (fileExists) View.VISIBLE else View.GONE
            binding.buttonSave.setOnClickListener { onSave(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<ImageRecord>() {
        override fun areItemsTheSame(o: ImageRecord, n: ImageRecord) = o.id == n.id
        override fun areContentsTheSame(o: ImageRecord, n: ImageRecord) = o == n
    }
}
