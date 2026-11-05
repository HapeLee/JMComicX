package dev.jmx.client

/** 只缓存颜色，不持有位图；透明或缺乏有效像素的边缘交给当前主题。 */
internal data class ReaderEdgeColors(val top: Int?, val bottom: Int?)

internal fun readerEdgeColors(width: Int, height: Int, pixel: (Int, Int) -> Int): ReaderEdgeColors {
    if (width <= 0 || height <= 0) return ReaderEdgeColors(null, null)
    fun sample(bottom: Boolean): Int? {
        val counts = IntArray(4096)
        val reds = IntArray(4096)
        val greens = IntArray(4096)
        val blues = IntArray(4096)
        val insetX = (width * .0275f).toInt()
        val insetY = (height * .0125f).toInt()
        val strip = (height * .06f).toInt().coerceIn(1, 16).coerceAtMost(height - insetY)
        val columns = (width - insetX * 2).coerceIn(1, 96)
        val rows = strip.coerceAtMost(8)
        var valid = 0
        repeat(rows) { row ->
            val depth = insetY + row * strip / rows
            val y = if (bottom) height - 1 - depth else depth
            repeat(columns) { column ->
                val x = (insetX + column * (width - insetX * 2) / columns).coerceIn(0, width - 1)
                val color = pixel(x, y.coerceIn(0, height - 1))
                if ((color ushr 24) >= 128) {
                    val r = color ushr 16 and 255
                    val g = color ushr 8 and 255
                    val b = color and 255
                    val bucket = ((r / 24) shl 8) or ((g / 24) shl 4) or (b / 24)
                    counts[bucket]++
                    reds[bucket] += r; greens[bucket] += g; blues[bucket] += b
                    valid++
                }
            }
        }
        if (valid < (columns * rows + 3) / 4) return null
        val dominant = counts.indices.maxByOrNull { counts[it] } ?: return null
        val count = counts[dominant]
        if (count == 0) return null
        return (255 shl 24) or ((reds[dominant] / count) shl 16) or
            ((greens[dominant] / count) shl 8) or (blues[dominant] / count)
    }
    return ReaderEdgeColors(sample(false), sample(true))
}
