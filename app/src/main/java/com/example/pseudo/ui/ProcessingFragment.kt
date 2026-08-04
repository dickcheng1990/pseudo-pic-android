package com.example.pseudo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.pseudo.R
import com.example.pseudo.databinding.FragmentProcessingBinding
import com.example.pseudo.models.ImageSelection
import com.example.pseudo.models.ProcessingParams
import com.example.pseudo.processors.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProcessingFragment : Fragment() {
    
    private var _binding: FragmentProcessingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: ProcessingResultAdapter
    private var selectedImages: List<ImageSelection> = emptyList()
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProcessingBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedImages = arguments?.getParcelableArray("selected_images")?.map { it as ImageSelection } ?: emptyList()
        setupRecyclerView()
        setupParameters()
        setupButtons()
        binding.textViewImageCount.text = "共 ${selectedImages.size} 张图片待处理"
    }
    
    private fun setupRecyclerView() {
        adapter = ProcessingResultAdapter()
        binding.recyclerViewResults.layoutManager = android.widget.LinearLayoutManager(requireContext())
        binding.recyclerViewResults.adapter = adapter
    }
    
    private fun setupParameters() {
        binding.seekBarCropProgress.progress = 15
        binding.seekBarColorShift.progress = 30
        binding.seekBarBrightness.progress = 20
        binding.seekBarNoise.progress = 15
        binding.seekBarInterference.progress = 30
        binding.switchWatermark.isChecked = true
        
        binding.seekBarCropProgress.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textViewCropValue.text = "${progress / 10.0}%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.seekBarColorShift.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textViewColorValue.text = "±${progress / 10.0}%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.seekBarBrightness.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textViewBrightnessValue.text = "±${progress / 10.0}%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.seekBarNoise.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textViewNoiseValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.seekBarInterference.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textViewInterferenceValue.text = "${progress}%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.switchDeepAi.setOnCheckedChangeListener { _, _ ->
            Toast.makeText(requireContext(), "深度AI模式当前版本不可用，将使用算法模式", Toast.LENGTH_SHORT).show()
            binding.switchDeepAi.isChecked = false
        }
    }
    
    private fun setupButtons() {
        binding.buttonProcess.setOnClickListener { startProcessing() }
        binding.buttonBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
    }
    
    private fun startProcessing() {
        val params = ProcessingParams(
            cropAmount = binding.seekBarCropProgress.progress / 10.0f,
            colorShift = binding.seekBarColorShift.progress / 10.0f,
            brightnessShift = binding.seekBarBrightness.progress / 10.0f,
            noiseIntensity = binding.seekBarNoise.progress.toFloat(),
            interferenceDensity = binding.seekBarInterference.progress / 100.0f,
            watermarkEnabled = binding.switchWatermark.isChecked,
            watermarkText = binding.editTextWatermark.text.toString(),
            useDeepAI = binding.switchDeepAi.isChecked,
            dctPerturbation = true
        )
        
        binding.buttonProcess.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            val results = processImages(selectedImages, params)
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.buttonProcess.isEnabled = true
                adapter.submitList(results)
                val successCount = results.count { it.success }
                Toast.makeText(requireContext(), "处理完成！成功 ${successCount}/${results.size}", Toast.LENGTH_SHORT).show()
                viewModel.saveProcessingResults(results)
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }
    
    private suspend fun processImages(images: List<ImageSelection>, params: ProcessingParams): List<com.example.pseudo.models.ProcessingResult> {
        val processor = ImageProcessor()
        val pairs = images.map { img ->
            val outputDir = File(img.path).parent
            val name = File(img.path).nameWithoutExtension
            val ext = File(img.path).extension
            Pair(img.path, "$outputDir/${name}_pseudo.$ext")
        }
        return processor.processBatch(pairs, params)
    }
    
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
