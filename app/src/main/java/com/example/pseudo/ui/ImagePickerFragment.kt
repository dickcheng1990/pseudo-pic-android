package com.example.pseudo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pseudo.R
import com.example.pseudo.databinding.FragmentImagePickerBinding
import com.example.pseudo.models.ImageSelection
import java.io.File

class ImagePickerFragment : Fragment() {
    
    private var _binding: FragmentImagePickerBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ImageSelectionAdapter
    private val selectedImages = mutableListOf<ImageSelection>()
    
    private val multipleGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris != null) {
            for (uri in uris) {
                addImageFromUri(uri)
            }
        }
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentImagePickerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupButtons()
    }
    
    private fun setupRecyclerView() {
        adapter = ImageSelectionAdapter(selectedImages) { pos ->
            selectedImages.removeAt(pos)
            adapter.notifyItemRemoved(pos)
            updateSelectionCount()
        }
        binding.recyclerViewImages.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = this@ImagePickerFragment.adapter
        }
    }
    
    private fun setupButtons() {
        binding.buttonSelectImages.setOnClickListener { multipleGalleryLauncher.launch(arrayOf("image/*")) }
        binding.buttonStartProcessing.setOnClickListener {
            if (selectedImages.isEmpty()) {
                Toast.makeText(requireContext(), "请先选择图片", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val args = bundleOf("selected_images" to selectedImages.toTypedArray())
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProcessingFragment().also { it.arguments = args })
                .addToBackStack(null).commit()
        }
    }
    
    private fun addImageFromUri(uri: android.net.Uri) {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        val nameIdx = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
        cursor?.moveToFirst()
        val filename = cursor?.getString(nameIdx ?: -1) ?: "image"
        val fileSize = cursor?.getLong(sizeIdx ?: -1) ?: 0L
        val path = resolvePath(uri, filename)
        val (w, h) = getImageDimensions(uri)
        cursor?.close()
        if (path != null) {
            selectedImages.add(ImageSelection(path, filename, fileSize, w, h))
            adapter.notifyItemInserted(selectedImages.size - 1)
            updateSelectionCount()
        }
    }
    
    private fun getRealPathFromURI(uri: android.net.Uri): String? {
        val proj = arrayOf(android.provider.MediaStore.Images.Media.DATA)
        val c = requireContext().contentResolver.query(uri, proj, null, null, null)
        c?.moveToFirst()
        val idx = c?.getColumnIndex(proj[0])
        val path = c?.getString(idx ?: -1)
        c?.close()
        return path
    }

    private fun resolvePath(uri: android.net.Uri, fallbackName: String): String? {
        getRealPathFromURI(uri)?.let { return it }
        return try {
            val dir = File(requireContext().cacheDir, "selected_images").apply { mkdirs() }
            val safeName = fallbackName.replace("[", "_").replace("]", "_")
            val target = File(dir, safeName)
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getImageDimensions(uri: android.net.Uri): Pair<Int, Int> {
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            requireContext().contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            Pair(opts.outWidth, opts.outHeight)
        } catch (e: Exception) { Pair(0, 0) }
    }
    
    private fun updateSelectionCount() {
        binding.textViewSelectionCount.text = "${selectedImages.size} 张图片已选择"
        binding.buttonStartProcessing.isEnabled = selectedImages.isNotEmpty()
    }
    
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    fun getSelectedImages(): List<ImageSelection> = selectedImages.toList()
}
