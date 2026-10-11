package com.lchat.app.util

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object AppUtils {

    private val avatarColors = intArrayOf(
        Color.parseColor("#FFFC00"),
        Color.parseColor("#2EC4FF"),
        Color.parseColor("#FF5C8A"),
        Color.parseColor("#9B6BFF"),
        Color.parseColor("#2DD4A0"),
        Color.parseColor("#FF9F43")
    )

    private val yellow = Color.parseColor("#FFFC00")

    fun initials(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) return "?"
        val parts = clean.split(Regex("\\s+"))
        return if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
            (parts[0].first().toString() + parts[1].first().toString()).uppercase()
        } else {
            clean.take(2).uppercase()
        }
    }

    fun avatarColor(seed: String): Int = avatarColors[abs(seed.hashCode()) % avatarColors.size]

    fun initialsTextColor(background: Int): Int =
        if (background == yellow) Color.BLACK else Color.WHITE

    fun circle(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun ring(strokeColor: Int, strokeDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke((strokeDp * 2).toInt().coerceAtLeast(1), strokeColor)
    }

    fun dp(value: Float): Int = (value * 2).toInt().coerceAtLeast(1)

    fun formatTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        if (diff < 0) return "now"
        if (diff < 60_000L) return "now"
        if (diff < 3_600_000L) return "${diff / 60_000}m"
        if (diff < 86_400_000L) return "${diff / 3_600_000}h"
        return SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }

    fun pairKey(a: String, b: String): String = if (a < b) "${a}_$b" else "${b}_$a"
}
