package com.itmakesome.pickupmemo2.matcher

data class AddressPair(val pickup: String, val dest: String) {
    /** 메모없음 케이스 dedup 키(FEAT-20). */
    fun key(): String = "$pickup|$dest"
}

object AddressExtractor {
    private const val KEY_PICKUP = "픽업지"
    private const val KEY_DEST = "전달지"

    fun extract(segments: List<String>, fullText: String): AddressPair? {
        var pickup = sourceA(segments, KEY_PICKUP)
        var dest = sourceA(segments, KEY_DEST)
        if (pickup.isNullOrBlank() || dest.isNullOrBlank()) {
            val b = sourceB(segments, fullText)
            if (pickup.isNullOrBlank()) pickup = b?.first
            if (dest.isNullOrBlank()) dest = b?.second
        }
        if (pickup.isNullOrBlank() || dest.isNullOrBlank()) return null
        return AddressPair(pickup.trim(), dest.trim())
    }

    private fun sourceA(segments: List<String>, key: String): String? {
        val idx = segments.indexOfFirst { it.contains(key) }
        if (idx < 0 || idx + 1 >= segments.size) return null
        return cleanSegment(segments[idx + 1]).ifBlank { null }
    }

    private fun sourceB(segments: List<String>, fullText: String): Pair<String?, String?>? {
        val line = segments.firstOrNull {
            (it.contains("신규배차") || it.contains(",")) &&
                it.contains(KEY_PICKUP) && it.contains(KEY_DEST)
        } ?: fullText
        val tokens = line.replace("desc=", "").replace(Regex("id=\\S+"), "")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        val p = nextAfter(tokens, KEY_PICKUP)
        val d = nextAfter(tokens, KEY_DEST)
        if (p == null && d == null) return null
        return p to d
    }

    private fun nextAfter(tokens: List<String>, key: String): String? {
        val i = tokens.indexOfFirst { it.contains(key) }
        if (i >= 0) {
            val same = tokens[i].replace(key, "").trim()
            if (same.isNotBlank()) return same
            return tokens.getOrNull(i + 1)?.trim()?.ifBlank { null }
        }
        return null
    }

    private fun cleanSegment(seg: String): String {
        val noId = seg.replace(Regex("id=\\S+"), "").trim()
        val head = noId.substringBefore("|").trim()
        if (head.isNotBlank()) return head
        return noId.substringAfter("desc=", "").trim()
    }
}
