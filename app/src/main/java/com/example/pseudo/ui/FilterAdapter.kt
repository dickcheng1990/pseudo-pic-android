package com.example.pseudo.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pseudo.databinding.ItemFilterBinding
import com.example.pseudo.models.FilterDefs

class FilterAdapter(
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<FilterAdapter.ViewHolder>() {

    private var selectedIndex = 0

    fun setSelected(index: Int) {
        val old = selectedIndex
        selectedIndex = index
        if (old != index) {
            notifyItemChanged(old)
            notifyItemChanged(index)
        }
    }

    inner class ViewHolder(val binding: ItemFilterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val name = FilterDefs.names[position]
        holder.binding.chipFilter.text = name
        holder.binding.chipFilter.isChecked = position == selectedIndex
        holder.binding.chipFilter.setOnClickListener {
            setSelected(position)
            onSelect(position)
        }
    }

    override fun getItemCount() = FilterDefs.names.size
}
