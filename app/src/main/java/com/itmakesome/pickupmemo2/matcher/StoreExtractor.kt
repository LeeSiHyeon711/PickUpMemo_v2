package com.itmakesome.pickupmemo2.matcher

object StoreExtractor {
    private const val KEY_CARD = "신규배차_카드"
    private const val KEY_PICKUP = "픽업지"
    private const val KEY_DEST = "전달지"

    fun extract(fullText: String?): String? {
        if (fullText.isNullOrBlank()) return null
        val hasCard = fullText.contains(KEY_CARD)
        val hasPickupDest = fullText.contains(KEY_PICKUP) && fullText.contains(KEY_DEST)
        if (!hasCard && !hasPickupDest) return null
        if (!fullText.contains(KEY_PICKUP) || !fullText.contains(KEY_DEST)) return null
        val between = fullText.substringAfter(KEY_PICKUP).substringBefore(KEY_DEST)
        val cleaned = between
            .replace(Regex("id=\\S+"), " ")
            .replace(Regex("desc=[^/|,]*"), " ")
            .replace('/', ' ').replace('|', ' ').replace(',', ' ')
            .replace(Regex("\\s+"), " ").trim()
        return cleaned.ifBlank { null }
    }
}
