package com.example.pseudo.ui

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pseudo.R
import com.example.pseudo.databinding.FragmentHistoryBinding
import com.example.pseudo.database.ImageRecord
import com.example.pseudo.utils.MediaStoreUtils
import com.example.pseudo.utils.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: HistoryAdapter
    private var pendingSavePath: String? = null

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val path = pendingSavePath
        pendingSavePath = null
        if (granted && path != null) {
            saveToGallery(path)
        } else {
            Toast.makeText(requireContext(), "需要存储权限才能保存到相册", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = HistoryAdapter(onSave = { record ->
            saveRecordToGallery(record)
        })
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewHistory.adapter = adapter
        loadHistory()
        binding.buttonClearHistory.setOnClickListener { viewModel.clearHistory() }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            viewModel.historyFlow.collectLatest { records ->
                adapter.submitList(records)
                binding.textViewEmptyState.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerViewHistory.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun saveRecordToGallery(record: ImageRecord) {
        val path = record.outputPath
        if (path.isEmpty()) return
        if (!PermissionUtils.hasWriteStoragePermission(requireContext())) {
            pendingSavePath = path
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveToGallery(path)
    }

    private fun saveToGallery(path: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = MediaStoreUtils.saveImageToGallery(requireContext(), path)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    if (ok) "已保存到相册 PseudoPic 文件夹" else "保存失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
