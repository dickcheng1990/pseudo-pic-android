package com.example.pseudo.ui

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pseudo.R
import com.example.pseudo.databinding.FragmentProcessingBinding
import com.example.pseudo.models.FilterDefs
import com.example.pseudo.models.ImageSelection
import com.example.pseudo.models.ProcessingParams
import com.example.pseudo.models.ProcessingResult
import com.example.pseudo.models.ProcessingTemplate
import com.example.pseudo.processors.ImageProcessor
import com.example.pseudo.utils.MediaStoreUtils
import com.example.pseudo.utils.PermissionUtils
import com.example.pseudo.utils.TemplateStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProcessingFragment : Fragment() {

    private var _binding: FragmentProcessingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: ProcessingResultAdapter
    private lateinit var filterAdapter: FilterAdapter
    private lateinit var templateAdapter: TemplateAdapter
    private var selectedImages: List<ImageSelection> = emptyList()
    private var processingDone = false
    private var pendingSavePath: String? = null
    private var lastResults: List<ProcessingResult> = emptyList()
    private var selectedFilter: Int = FilterDefs.NONE
    private var templates: List<ProcessingTemplate> = emptyList()

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
        _binding = FragmentProcessingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        @Suppress("DEPRECATION")
        selectedImages = arguments?.getParcelableArray("selected_images")?.map { it as ImageSelection } ?: emptyList()

        setupRecyclerView()
        setupTemplates()
        setupFilters()
        setupParameters()
        setupButtons()
        binding.textViewImageCount.text = "共 ${selectedImages.size} 张图片待处理"
    }

    private fun setupRecyclerView() {
        adapter = ProcessingResultAdapter(onSave = { result ->
            saveResultToGallery(result)
        })
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewResults.adapter = adapter
    }

    private fun setupTemplates() {
        templates = TemplateStore.load(requireContext())
        templateAdapter = TemplateAdapter(
            onApply = { template -> applyTemplate(template) },
            onDelete = { template -> confirmDeleteTemplate(template) }
        )
        binding.recyclerViewTemplates.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerViewTemplates.adapter = templateAdapter
        templateAdapter.submitList(templates)

        binding.buttonSaveTemplate.setOnClickListener { promptTemplateName() }
    }

    private fun setupFilters() {
        filterAdapter = FilterAdapter(onSelect = { index ->
            selectedFilter = index
        })
        binding.recyclerViewFilters.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerViewFilters.adapter = filterAdapter
        filterAdapter.setSelected(FilterDefs.NONE)
    }

    private fun setupParameters() {
        binding.seekBarCropProgress.progress = 15
        binding.seekBarColorShift.progress = 30
        binding.seekBarBrightness.progress = 20
        binding.seekBarNoise.progress = 15
        binding.seekBarInterference.progress = 30
        binding.switchWatermark.isChecked = true
        binding.seekBarCropZoom.progress = 20
        binding.seekBarRotate.progress = 10
        binding.seekBarFilterStrength.progress = 100

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
        binding.seekBarCropZoom.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textViewCropZoomValue.text = "${progress / 10.0}%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.seekBarRotate.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textViewRotateValue.text = "${progress / 10.0}°"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.seekBarFilterStrength.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textViewFilterStrengthValue.text = "${progress}%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        binding.switchDeepAi.setOnCheckedChangeListener { _, isChecked ->
            binding.textViewDeepHint.text = if (isChecked) {
                "深度模式：更强防查重，处理时间较长"
            } else {
                ""
            }
        }
    }

    private fun setupButtons() {
        binding.buttonProcess.setOnClickListener {
            if (processingDone) {
                requireActivity().supportFragmentManager.popBackStack()
            } else {
                startProcessing()
            }
        }
        binding.buttonBack.setOnClickListener {
            if (!processingDone) {
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
        binding.buttonSaveAll.setOnClickListener { saveAllToGallery() }
    }

    private fun startProcessing() {
        val params = readParamsFromUi()

        binding.buttonProcess.isEnabled = false
        binding.buttonBack.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.textViewImageCount.text = "正在处理 ${selectedImages.size} 张图片，请稍候..."

        lifecycleScope.launch(Dispatchers.IO) {
            val results = processImages(selectedImages, params)
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.buttonProcess.isEnabled = true
                binding.buttonBack.isEnabled = true
                processingDone = true
                binding.buttonProcess.text = "完成"

                adapter.submitList(results)
                binding.recyclerViewResults.visibility = View.VISIBLE
                binding.textViewImageCount.text = "处理完成，可查看结果并保存到相册"

                lastResults = results
                binding.buttonSaveAll.visibility = View.VISIBLE

                val successCount = results.count { it.success }
                Toast.makeText(
                    requireContext(),
                    "处理完成！成功 $successCount/${results.size}",
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.saveProcessingResults(results)
            }
        }
    }

    private fun readParamsFromUi(): ProcessingParams = ProcessingParams(
        cropAmount = binding.seekBarCropProgress.progress / 10.0f,
        colorShift = binding.seekBarColorShift.progress / 10.0f,
        brightnessShift = binding.seekBarBrightness.progress / 10.0f,
        noiseIntensity = binding.seekBarNoise.progress.toFloat(),
        interferenceDensity = binding.seekBarInterference.progress / 100.0f,
        watermarkEnabled = binding.switchWatermark.isChecked,
        watermarkText = binding.editTextWatermark.text.toString(),
        useDeepAI = binding.switchDeepAi.isChecked,
        dctPerturbation = true,
        cropZoomPercent = binding.seekBarCropZoom.progress / 10.0f,
        rotateDegrees = binding.seekBarRotate.progress / 10.0f,
        filterType = selectedFilter,
        filterStrength = binding.seekBarFilterStrength.progress / 100f
    )

    private fun applyParamsToUi(p: ProcessingParams) {
        binding.seekBarCropProgress.progress = (p.cropAmount * 10).toInt()
        binding.seekBarColorShift.progress = (p.colorShift * 10).toInt()
        binding.seekBarBrightness.progress = (p.brightnessShift * 10).toInt()
        binding.seekBarNoise.progress = p.noiseIntensity.toInt()
        binding.seekBarInterference.progress = (p.interferenceDensity * 100).toInt()
        binding.seekBarCropZoom.progress = (p.cropZoomPercent * 10).toInt()
        binding.seekBarRotate.progress = (p.rotateDegrees * 10).toInt()
        binding.switchWatermark.isChecked = p.watermarkEnabled
        binding.editTextWatermark.setText(p.watermarkText)
        binding.switchDeepAi.isChecked = p.useDeepAI
        binding.textViewDeepHint.text = if (p.useDeepAI) "深度模式：更强防查重，处理时间较长" else ""

        binding.textViewCropValue.text = "${p.cropAmount}%"
        binding.textViewColorValue.text = "±${p.colorShift}%"
        binding.textViewBrightnessValue.text = "±${p.brightnessShift}%"
        binding.textViewNoiseValue.text = p.noiseIntensity.toInt().toString()
        binding.textViewInterferenceValue.text = "${(p.interferenceDensity * 100).toInt()}%"
        binding.textViewCropZoomValue.text = "${p.cropZoomPercent}%"
        binding.textViewRotateValue.text = "${p.rotateDegrees}°"

        binding.seekBarFilterStrength.progress = (p.filterStrength * 100).toInt().coerceIn(0, 100)
        binding.textViewFilterStrengthValue.text = "${(p.filterStrength * 100).toInt()}%"

        selectedFilter = p.filterType
        filterAdapter.setSelected(p.filterType)
    }

    private fun promptTemplateName() {
        val input = EditText(requireContext())
        input.hint = "模板名称"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("保存为模板")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入模板名称", Toast.LENGTH_SHORT).show()
                } else {
                    saveTemplate(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveTemplate(name: String) {
        val template = ProcessingTemplate(System.currentTimeMillis(), name, readParamsFromUi())
        templates = templates + template
        TemplateStore.saveAll(requireContext(), templates)
        templateAdapter.submitList(templates)
        Toast.makeText(requireContext(), "模板已保存：$name", Toast.LENGTH_SHORT).show()
    }

    private fun applyTemplate(template: ProcessingTemplate) {
        applyParamsToUi(template.params)
        Toast.makeText(requireContext(), "已应用模板：${template.name}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteTemplate(template: ProcessingTemplate) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除模板")
            .setMessage("确定删除模板 \"${template.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                templates = templates.filter { it.id != template.id }
                TemplateStore.saveAll(requireContext(), templates)
                templateAdapter.submitList(templates)
                Toast.makeText(requireContext(), "模板已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveAllToGallery() {
        val paths = lastResults.mapNotNull { if (it.success) it.outputPath else null }
        if (paths.isEmpty()) {
            Toast.makeText(requireContext(), "没有可保存的图片", Toast.LENGTH_SHORT).show()
            return
        }
        binding.buttonSaveAll.isEnabled = false
        binding.textViewImageCount.text = "正在保存 ${paths.size} 张图片到相册..."
        lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            paths.forEach { path ->
                if (MediaStoreUtils.saveImageToGallery(requireContext(), path)) success++
            }
            val saved = success
            val total = paths.size
            withContext(Dispatchers.Main) {
                binding.buttonSaveAll.isEnabled = true
                binding.textViewImageCount.text = "已保存 $saved/$total 张到相册 PseudoPic 文件夹"
                Toast.makeText(
                    requireContext(),
                    "已保存 $saved/$total 张图片到相册", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveResultToGallery(result: ProcessingResult) {
        if (!result.success) return
        val outputPath = result.outputPath ?: return
        if (!PermissionUtils.hasWriteStoragePermission(requireContext())) {
            pendingSavePath = outputPath
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveToGallery(outputPath)
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

    private suspend fun processImages(
        images: List<ImageSelection>,
        params: ProcessingParams
    ): List<ProcessingResult> {
        val processor = ImageProcessor()
        val pairs = images.map { img ->
            val outputDir = File(img.path).parent
            val name = File(img.path).nameWithoutExtension
            val ext = File(img.path).extension
            Pair(img.path, "$outputDir/${name}_pseudo.$ext")
        }
        return processor.processBatch(pairs, params)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
