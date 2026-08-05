package com.example.pseudo.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pseudo.databinding.ItemFilterBinding
import com.example.pseudo.models.FilterDefs

class FilterAdapter(
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<FilterAdapter.ViewHolder>() {

    private var selectedIndex = 0

    companion object {
        private val SELECTED_BG = Color.parseColor("#4CAF50")
        private val UNSELECTED_BG = Color.parseColor("#F44336")
    }

    fun setSelected(index: Int) {
        if (index < 0 || index >= itemCount) return
        val old = selectedIndex
        if (old == index) return
        selectedIndex = index
        notifyItemChanged(old)
        notifyItemChanged(index)
    }

    fun getSelected(): Int = selectedIndex

    inner class ViewHolder(val binding: ItemFilterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chip = holder.binding.chipFilter
        val isSelected = position == selectedIndex

        chip.text = FilterDefs.names[position]
        chip.isChecked = isSelected
        // Force colors in code so rendering never depends on XML selector state
        chip.setChipBackgroundColor(ColorStateList.valueOf(if (isSelected) SELECTED_BG else UNSELECTED_BG))
        chip.setTextColor(if (isSelected) Color.WHITE else Color.BLACK)

        chip.setOnClickListener {
            if (selectedIndex != position) {
                setSelected(position)
                onSelect(position)
            }
        }
    }

    override fun getItemCount() = FilterDefs.names.size
}
