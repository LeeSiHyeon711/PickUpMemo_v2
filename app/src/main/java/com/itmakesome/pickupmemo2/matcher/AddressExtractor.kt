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

    /**
     * 라벨(픽업지/전달지) 세그먼트의 "다음 형제"를 값으로 취한다.
     * 픽업지·전달지를 동시에 포함하는 "집계 desc" 세그먼트(예: 신규배차 카드 요약)는
     * 순수 라벨 세그먼트가 아니므로 매칭에서 제외한다(#27) — 그렇지 않으면
     * 그 다음 형제(대개 상호명 등 부가정보)를 주소로 오추출하게 된다.
     */
    private fun sourceA(segments: List<String>, key: String): String? {
        val otherKey = if (key == KEY_PICKUP) KEY_DEST else KEY_PICKUP
        val idx = segments.indexOfFirst { it.contains(key) && !it.contains(otherKey) }
        if (idx < 0 || idx + 1 >= segments.size) return null
        return sanitizeValue(cleanSegment(segments[idx + 1]))
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
            val same = sanitizeValue(tokens[i].replace(key, "").trim())
            if (same != null) return same
            return tokens.getOrNull(i + 1)?.trim()?.let { sanitizeValue(it) }
        }
        return null
    }

    private fun cleanSegment(seg: String): String {
        val noId = seg.replace(Regex("id=\\S+"), "").trim()
        val head = noId.substringBefore("|").trim()
        if (head.isNotBlank()) return head
        return noId.substringAfter("desc=", "").trim()
    }

    /**
     * 마스킹용 "****" 등 노이즈를 제거하고, 라벨성/부가정보 토큰(예: "포인트", "9.9P")이
     * 값으로 잘못 채택된 경우 무효(null) 처리한다(#27 보강 — 견고성).
     */
    private fun sanitizeValue(raw: String): String? {
        val cleaned = raw.replace(Regex("\\*+"), "").replace(Regex(" {2,}"), " ").trim()
        if (cleaned.isBlank()) return null
        if (cleaned == KEY_PICKUP || cleaned == KEY_DEST) return null
        if (cleaned == "포인트" || Regex("^\\d+(\\.\\d+)?P$").matches(cleaned)) return null
        return cleaned
    }
}
