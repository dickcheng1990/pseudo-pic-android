package com.example.pseudo.models

/**
 * Filter presets tuned for lifestyle/food photos (Dianping-style).
 * 0 = none; 1..7 are color-grading presets applied via ColorMatrix.
 */
object FilterDefs {

    const val NONE = 0
    const val NATURAL_FOOD = 1
    const val BRIGHT_FOOD = 2
    const val FRESH = 3
    const val RETRO_FILM = 4
    const val MONO = 5
    const val WARM_SUN = 6
    const val COOL_MINIMAL = 7

    val names = listOf(
        "无滤镜",
        "自然美食",
        "鲜亮美食",
        "清新自然",
        "复古胶片",
        "黑白经典",
        "暖阳",
        "冷调极简"
    )
}
