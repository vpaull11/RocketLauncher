package com.rocketlauncher.util

object SemVer {

    fun compareNormalized(a: String, b: String): Int {
        val pa = parseParts(a)
        val pb = parseParts(b)
        val n = kotlin.math.max(pa.size, pb.size)
        for (i in 0 until n) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    fun normalizeTag(tag: String): String =
        tag.trim().removePrefix("v").substringBefore('-').trim()

    private fun parseParts(s: String): List<Int> {
        val base = normalizeTag(s)
        if (base.isEmpty()) return listOf(0)
        return base.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }
}
