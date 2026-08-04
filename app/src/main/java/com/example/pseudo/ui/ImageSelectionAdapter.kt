package com.example.pseudo.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.pseudo.databinding.ItemImageSelectionBinding
import com.example.pseudo.models.ImageSelection

class ImageSelectionAdapter(
    private val images: MutableList<ImageSelection>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ImageSelectionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemImageSelectionBinding) : RecyclerView.ViewHolder(binding.root) {
        var currentBitmap: Bitmap? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemImageSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val img = images[position]

        // Recycle previous thumbnail to prevent memory leaks
        holder.currentBitmap?.let { old ->
            if (!old.isRecycled) old.recycle()
        }
        holder.currentBitmap = null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, if (img.width > 0) img.width / 100 else 1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(img.path, opts)
        holder.currentBitmap = bitmap
        holder.binding.imageViewThumbnail.setImageBitmap(bitmap)
        holder.binding.textViewName.text =
            if (img.filename.length > 15) img.filename.substring(0, 15) + "..." else img.filename
        holder.binding.buttonRemove.setOnClickListener { onRemove(position) }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.currentBitmap?.let { old ->
            if (!old.isRecycled) old.recycle()
        }
        holder.currentBitmap = null
    }

    override fun getItemCount() = images.size
}
