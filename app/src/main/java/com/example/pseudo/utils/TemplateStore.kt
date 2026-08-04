package com.example.pseudo.utils

import android.content.Context
import com.example.pseudo.models.ProcessingParams
import com.example.pseudo.models.ProcessingTemplate
import org.json.JSONArray
import org.json.JSONObject

object TemplateStore {

    private const val PREFS = "template_prefs"
    private const val KEY = "templates"

    fun load(context: Context): List<ProcessingTemplate> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                ProcessingTemplate(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    params = parseParams(obj.getJSONObject("params"))
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun saveAll(context: Context, templates: List<ProcessingTemplate>) {
        val arr = JSONArray()
        templates.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("params", paramsToJson(t.params))
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    fun paramsToJson(p: ProcessingParams): JSONObject = JSONObject().apply {
        put("cropAmount", p.cropAmount.toDouble())
        put("colorShift", p.colorShift.toDouble())
        put("brightnessShift", p.brightnessShift.toDouble())
        put("noiseIntensity", p.noiseIntensity.toDouble())
        put("interferenceDensity", p.interferenceDensity.toDouble())
        put("watermarkEnabled", p.watermarkEnabled)
        put("watermarkText", p.watermarkText)
        put("useDeepAI", p.useDeepAI)
        put("dctPerturbation", p.dctPerturbation)
        put("cropZoomPercent", p.cropZoomPercent.toDouble())
        put("rotateDegrees", p.rotateDegrees.toDouble())
        put("filterType", p.filterType)
    }

    fun parseParams(o: JSONObject): ProcessingParams = ProcessingParams(
        cropAmount = o.optDouble("cropAmount", 1.5).toFloat(),
        colorShift = o.optDouble("colorShift", 3.0).toFloat(),
        brightnessShift = o.optDouble("brightnessShift", 2.0).toFloat(),
        noiseIntensity = o.optDouble("noiseIntensity", 15.0).toFloat(),
        interferenceDensity = o.optDouble("interferenceDensity", 0.3).toFloat(),
        watermarkEnabled = o.optBoolean("watermarkEnabled", true),
        watermarkText = o.optString("watermarkText", ""),
        useDeepAI = o.optBoolean("useDeepAI", false),
        dctPerturbation = o.optBoolean("dctPerturbation", true),
        cropZoomPercent = o.optDouble("cropZoomPercent", 2.0).toFloat(),
        rotateDegrees = o.optDouble("rotateDegrees", 1.0).toFloat(),
        filterType = o.optInt("filterType", 0)
    )
}
