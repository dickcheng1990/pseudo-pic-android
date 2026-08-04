package com.example.pseudo.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pseudo.databinding.ItemTemplateBinding
import com.example.pseudo.models.ProcessingTemplate

class TemplateAdapter(
    private val onApply: (ProcessingTemplate) -> Unit,
    private val onDelete: (ProcessingTemplate) -> Unit
) : ListAdapter<ProcessingTemplate, TemplateAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemTemplateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(template: ProcessingTemplate) {
            binding.textViewTemplateName.text = template.name
            binding.root.setOnClickListener { onApply(template) }
            binding.buttonDeleteTemplate.setOnClickListener { onDelete(template) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTemplateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<ProcessingTemplate>() {
        override fun areItemsTheSame(o: ProcessingTemplate, n: ProcessingTemplate) = o.id == n.id
        override fun areContentsTheSame(o: ProcessingTemplate, n: ProcessingTemplate) = o == n
    }
}
