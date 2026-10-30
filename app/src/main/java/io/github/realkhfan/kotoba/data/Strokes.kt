package io.github.realkhfan.kotoba.data

import kotlin.math.hypot

/** A point on KanjiVG's 109 x 109 grid. */
data class Pt(val x: Float, val y: Float)

typealias Stroke = List<Pt>

object Strokes {
    const val GRID = 109f

    /** Reads assets/strokes.txt (see tools/strokes.py). */
    fun parse(text: String): Map<String, List<Stroke>> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .associate { line ->
            val (char, data) = line.split('\t', limit = 2)
            char to data.split('|').map { stroke ->
                stroke.split(' ').map { p ->
                    val (x, y) = p.split(',')
                    Pt(x.toInt() / 10f, y.toInt() / 10f)
                }
            }
        }

    fun length(s: Stroke) = s.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()

    /** [n] points evenly spaced along the stroke. */
    fun resample(s: Stroke, n: Int = 24): Stroke {
        if (s.size < 2) return List(n) { s.firstOrNull() ?: Pt(0f, 0f) }
        val total = length(s)
        if (total == 0f) return List(n) { s[0] }
        val out = ArrayList<Pt>(n)
        var seg = 0
        var segStart = 0f
        for (k in 0 until n) {
            val target = total * k / (n - 1)
            while (seg < s.size - 2 && segStart + hypot(s[seg + 1].x - s[seg].x, s[seg + 1].y - s[seg].y) < target) {
                segStart += hypot(s[seg + 1].x - s[seg].x, s[seg + 1].y - s[seg].y)
                seg++
            }
            val a = s[seg]
            val b = s[seg + 1]
            val len = hypot(b.x - a.x, b.y - a.y)
            val t = if (len == 0f) 0f else ((target - segStart) / len).coerceIn(0f, 1f)
            out += Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        return out
    }

    private fun meanDistance(a: Stroke, b: Stroke) = a.indices.map { hypot(a[it].x - b[it].x, a[it].y - b[it].y) }.average().toFloat()

    /**
     * True when a drawn stroke follows the expected one: close on average, starting and ending
     * near the right places (so the direction counts), and roughly as long. Units are grid units;
     * the limits are loose because a finger on a phone is not a brush.
     */
    fun matches(drawn: Stroke, expected: Stroke): Boolean {
        val expectedLength = length(expected)
        val drawnLength = length(drawn)
        // Very short strokes (dots) only need to start in the right place.
        if (expectedLength < 12f) {
            return drawn.isNotEmpty() && hypot(drawn[0].x - expected[0].x, drawn[0].y - expected[0].y) < 18f && drawnLength < 40f
        }
        if (drawn.size < 2 || drawnLength < expectedLength * 0.35f || drawnLength > expectedLength * 2.2f + 15f) return false
        val a = resample(drawn)
        val b = resample(expected)
        val forward = meanDistance(a, b)
        val backward = meanDistance(a, b.reversed())
        val start = hypot(a.first().x - b.first().x, a.first().y - b.first().y)
        val end = hypot(a.last().x - b.last().x, a.last().y - b.last().y)
        return forward < 14f && forward < backward && start < 22f && end < 22f
    }
}
