package com.example.domain

object TextAlignmentEngine {

    enum class DiffType {
        MATCH, SUBSTITUTION, INSERTION, DELETION
    }

    data class DiffToken(
        val ocrChar: String,
        val refChar: String,
        val type: DiffType
    )

    data class AlignmentResult(
        val similarityScore: Float, // 0.0 .. 100.0%
        val matches: Int,
        val substitutions: Int,
        val insertions: Int,
        val deletions: Int,
        val alignedOcr: String,
        val alignedRef: String,
        val tokens: List<DiffToken>
    )

    /**
     * Needle-Wunsch alignment algorithm for Arabic / Latin manuscript lines vs Canonical Text Witness
     */
    fun align(ocrText: String, refText: String): AlignmentResult {
        val s1 = ocrText.trim()
        val s2 = refText.trim()

        if (s1.isEmpty() && s2.isEmpty()) {
            return AlignmentResult(100f, 0, 0, 0, 0, "", "", emptyList())
        }
        if (s1.isEmpty()) {
            val tokens = s2.map { DiffToken("", it.toString(), DiffType.INSERTION) }
            return AlignmentResult(0f, 0, 0, s2.length, 0, "", s2, tokens)
        }
        if (s2.isEmpty()) {
            val tokens = s1.map { DiffToken(it.toString(), "", DiffType.DELETION) }
            return AlignmentResult(0f, 0, 0, 0, s1.length, s1, "", tokens)
        }

        val matchScore = 2
        val mismatchScore = -1
        val gapPenalty = -1

        val n = s1.length
        val m = s2.length
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) dp[i][0] = i * gapPenalty
        for (j in 0..m) dp[0][j] = j * gapPenalty

        for (i in 1..n) {
            for (j in 1..m) {
                val match = dp[i - 1][j - 1] + if (s1[i - 1] == s2[j - 1]) matchScore else mismatchScore
                val delete = dp[i - 1][j] + gapPenalty
                val insert = dp[i][j - 1] + gapPenalty
                dp[i][j] = maxOf(match, maxOf(delete, insert))
            }
        }

        // Traceback
        var i = n
        var j = m
        val tokensReversed = mutableListOf<DiffToken>()
        var matchCount = 0
        var subCount = 0
        var insCount = 0
        var delCount = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + (if (s1[i - 1] == s2[j - 1]) matchScore else mismatchScore)) {
                if (s1[i - 1] == s2[j - 1]) {
                    tokensReversed.add(DiffToken(s1[i - 1].toString(), s2[j - 1].toString(), DiffType.MATCH))
                    matchCount++
                } else {
                    tokensReversed.add(DiffToken(s1[i - 1].toString(), s2[j - 1].toString(), DiffType.SUBSTITUTION))
                    subCount++
                }
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + gapPenalty) {
                tokensReversed.add(DiffToken(s1[i - 1].toString(), "-", DiffType.DELETION))
                delCount++
                i--
            } else {
                tokensReversed.add(DiffToken("-", s2[j - 1].toString(), DiffType.INSERTION))
                insCount++
                j--
            }
        }

        val tokens = tokensReversed.reversed()
        val alignedOcr = tokens.map { it.ocrChar }.joinToString("")
        val alignedRef = tokens.map { it.refChar }.joinToString("")
        val totalLength = maxOf(n, m)
        val similarity = (matchCount.toFloat() / totalLength.coerceAtLeast(1)) * 100f

        return AlignmentResult(
            similarityScore = similarity.coerceIn(0f, 100f),
            matches = matchCount,
            substitutions = subCount,
            insertions = insCount,
            deletions = delCount,
            alignedOcr = alignedOcr,
            alignedRef = alignedRef,
            tokens = tokens
        )
    }
}
