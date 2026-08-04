package com.example.pseudo.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pseudo.databinding.ItemImageSelectionBinding
import com.example.pseudo.models.ImageSelection

class ImageSelectionAdapter(
    private val images: MutableList<ImageSelection>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ImageSelectionAdapter.ViewHolder>() {
    
    inner class ViewHolder(val binding: ItemImageSelectionBinding) : RecyclerView.ViewHolder(binding.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemImageSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val img = images[position]
        val opts = BitmapFactory.Options().apply { inSampleSize = if (img.width > 100) img.width / 100 else 1 }
        holder.binding.imageViewThumbnail.setImageBitmap(BitmapFactory.decodeFile(img.path, opts))
        holder.binding.textViewName.text = if (img.filename.length > 15) img.filename.substring(0, 15) + "..." else img.filename
        holder.binding.buttonRemove.setOnClickListener { onRemove(position) }
    }
    
    override fun getItemCount() = images.size
}
