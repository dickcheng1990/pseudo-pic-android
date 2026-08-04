package com.example.pseudo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pseudo.R
import com.example.pseudo.databinding.FragmentHistoryBinding
import com.example.pseudo.models.ImageRecord
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: HistoryAdapter
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = HistoryAdapter()
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
    
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
