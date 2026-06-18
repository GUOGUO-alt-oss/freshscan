package com.example.freshscan.domain.model

/**
 * Fruit/Vegetable category enum.
 * Each entry maps to two model output indices (fresh + rotten).
 * Display names are in Chinese (default locale).
 */
enum class FruitCategory(
    val displayName: String,
    val colorHex: Long
) {
    APPLE("苹果", 0xFFE53935),
    BANANA("香蕉", 0xFFFFD600),
    BITTER_GOURD("苦瓜", 0xFF4CAF50),
    CAPSICUM("甜椒", 0xFFE91E63),
    CUCUMBER("黄瓜", 0xFF8BC34A),
    OKRA("秋葵", 0xFF7CB342),
    ORANGE("橙子", 0xFFFF9800),
    POTATO("土豆", 0xFF795548),
    TOMATO("番茄", 0xFFF44336),
    UNKNOWN("未知", 0xFF9E9E9E);

    companion object {
        /**
         * Map a model output index (0-17) to a FruitCategory.
         * Indices 0-1 = APPLE, 2-3 = BANANA, ..., 16-17 = TOMATO.
         */
        fun fromLabelIndex(index: Int): FruitCategory {
            return entries.getOrElse(index / 2) { UNKNOWN }
        }
    }
}
