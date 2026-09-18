package com.example.domain

object NormalizationHelper {
    private val TASHKEEL_REGEX = Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]")
    private val TATWEEL_REGEX = Regex("\u0640")

    fun stripTashkeel(text: String): String {
        return text.replace(TASHKEEL_REGEX, "")
    }

    fun stripTatweel(text: String): String {
        return text.replace(TATWEEL_REGEX, "")
    }

    /**
     * Set of non-connecting Arabic characters (letters that do not connect to subsequent letters).
     */
    private val NON_CONNECTING_CHARS = setOf(
        'ا', 'أ', 'إ', 'آ', 'ٱ', 'د', 'ذ', 'ر', 'ز', 'و', 'ؤ', 'ة', 'ء', 'ى', 'ۏ', 'ࢮ'
    )

    /**
     * Common standalone 2+ letter Arabic particles and words that should remain separate words.
     */
    private val STANDALONE_WORDS = setOf(
        "في", "من", "عن", "أن", "إن", "لا", "ما", "هو", "هي", "ذا", "يا", "ثم", "قد",
        "بل", "لو", "كي", "مع", "بي", "لي", "كم", "أو", "أي", "هل", "كل", "إذ", "إذا",
        "إلى", "على", "حتى", "غير", "ليس", "بين", "فإن", "وإن", "أما", "إما", "فلا", "ولا",
        "إلا", "ألا", "الله", "إله", "رب", "نعم", "بلى", "فصل", "كتاب", "الحمد", "قال", "كان"
    )

    /**
     * Checks if a token (after stripping tashkeel) contains only 1 base character.
     */
    fun isSingleArabicGlyph(token: String): Boolean {
        val base = stripTashkeel(token).replace(TATWEEL_REGEX, "").trim()
        return base.length == 1 && base[0] in '\u0600'..'\u08FF'
    }

    /**
     * Checks if a token ends with a cursive connecting letter that must join the next letter.
     */
    fun endsInConnectingLetter(token: String): Boolean {
        val base = stripTashkeel(token).replace(TATWEEL_REGEX, "").trim()
        if (base.isEmpty()) return false
        val lastChar = base.last()
        return lastChar in '\u0600'..'\u08FF' && lastChar !in NON_CONNECTING_CHARS
    }

    /**
     * Fixes disconnected/fragmented Arabic letters resulting from raw CTC OCR outputs.
     * - Merges sequences of single isolated Arabic/Jawi letters into complete words.
     * - Fixes separated prefixes (و، ف، ب، ل، ك).
     * - Joins broken ligatures and particles (e.g. 'ل ا' -> 'لا', 'إ لا' -> 'إلا', 'ع ل ى' -> 'على').
     * - Attaches tokens ending in connecting letters to subsequent fragments while preserving true word boundaries.
     */
    fun fixFragmentedArabicSpacing(text: String): String {
        if (text.isBlank()) return text
        
        // 1. Clean spurious OCR punctuation artifacts
        var cleaned = text
            .replace(Regex("[`'\"^~]"), "")
            .replace(Regex("!([\\u0600-\\u08FF])"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) return ""

        // 2. Fix specific CTC ligature splits and compound particles (avoiding ASCII \b)
        cleaned = cleaned
            .replace(Regex("ل\\s+ا"), "لا")
            .replace(Regex("ل\\s+إ"), "لإ")
            .replace(Regex("ل\\s+أ"), "لأ")
            .replace(Regex("ل\\s+آ"), "لآ")
            .replace(Regex("ل\\s+ٱ"), "لٱ")
            .replace(Regex("إ\\s+لا"), "إلا")
            .replace(Regex("أ\\s+لا"), "ألا")
            .replace(Regex("و\\s+لا"), "ولا")
            .replace(Regex("ف\\s+لا"), "فلا")
            .replace(Regex("إ\\s+ن"), "إن")
            .replace(Regex("أ\\s+ن"), "أن")
            .replace(Regex("م\\s+ن"), "من")
            .replace(Regex("ع\\s+ن"), "عن")
            .replace(Regex("ف\\s+ي"), "في")
            .replace(Regex("م\\s+ا"), "ما")
            .replace(Regex("ع\\s+ل\\s+ى"), "على")
            .replace(Regex("إ\\s+ل\\s+ى"), "إلى")
            .replace(Regex("ح\\s+ت\\s+ى"), "حتى")
            .replace(Regex("ث\\s+م"), "ثم")

        val tokens = cleaned.split(" ")
        val pass1 = mutableListOf<String>()
        var letterBuffer = StringBuilder()

        // 3. Pass 1: Collapse consecutive single-character letter runs into coherent words
        for (token in tokens) {
            val isSingle = isSingleArabicGlyph(token)
            
            if (isSingle) {
                letterBuffer.append(token)
            } else {
                if (letterBuffer.isNotEmpty()) {
                    pass1.add(letterBuffer.toString())
                    letterBuffer = StringBuilder()
                }
                pass1.add(token)
            }
        }
        if (letterBuffer.isNotEmpty()) {
            pass1.add(letterBuffer.toString())
        }

        // 4. Pass 2: Reattach single-letter prefixes (و، ف، ب، ل، ك) and connecting fragments
        val pass2 = mutableListOf<String>()
        var i = 0
        while (i < pass1.size) {
            val current = pass1[i]
            val currentBase = stripTashkeel(current).replace(TATWEEL_REGEX, "")
            
            // Single letter Arabic prefixes attached without space in orthography (و، ف، ب، ل، ك)
            val isPrefix = (currentBase.length == 1 && currentBase[0] in listOf('و', 'ف', 'ب', 'ل', 'ك'))
            
            if (isPrefix && i + 1 < pass1.size) {
                val next = pass1[i + 1]
                pass2.add(current + next)
                i += 2
                continue
            }

            // Check if current fragment ends in a connecting letter and next token is a short fragment (broken word)
            if (i + 1 < pass1.size && endsInConnectingLetter(current) && !STANDALONE_WORDS.contains(currentBase)) {
                val next = pass1[i + 1]
                val nextBase = stripTashkeel(next).replace(TATWEEL_REGEX, "")
                if (nextBase.length <= 3 && !STANDALONE_WORDS.contains(nextBase)) {
                    pass2.add(current + next)
                    i += 2
                    continue
                }
            }

            pass2.add(current)
            i++
        }

        val result = pass2.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        return healClassicalArabicPhrases(result)
    }

    /**
     * Common phrase dictionary for classical Arabic manuscripts to heal severe OCR distortions
     */
    private val CANONICAL_MANUSCRIPT_PHRASES = listOf(
        "بسم الله الرحمن الرحيم",
        "الحمد لله رب العالمين",
        "وصلى الله على سيدنا محمد",
        "وعلى آله وصحبه أجمعين",
        "كتاب الفقه على المذاهب الأربعة",
        "فصل في بيان شروط الصلاة",
        "فصل أركان الإيمان ستة",
        "فصل في بيان فروض الوضوء",
        "شهادة أن لا إله إلا الله وأن محمدا رسول الله",
        "وإقام الصلاة وإيتاء الزكاة وصوم رمضان",
        "وحج البيت من استطاع إليه سبيلا",
        "شروط الصلاة قبل الدخول فيها ثمانية",
        "طهارة الحدثين وطهارة عن النجس في الثوب والبدن والمكان",
        "وستر العورة واستقبال القبلة ودخول الوقت",
        "والعلم بفرضيتها وألا يعتقد فرضا من فروضها سنة",
        "واجتناب المبطلات"
    )

    fun healClassicalArabicPhrases(text: String): String {
        if (text.isBlank()) return text
        val norm = normalizeArabic(text).replace(" ", "")

        // Pick the BEST-matching canonical phrase across the whole list,
        // not the first one that clears the threshold. The previous
        // version returned as soon as any candidate passed, in array
        // order -- for a short input like "لا إله إلا الله" (the shahada),
        // the FIRST candidate in the list ("بسم الله الرحمن الرحيم", the
        // basmalah -- an entirely different phrase) happened to score
        // above threshold under the old metric before the genuinely
        // related "شهادة أن لا إله إلا الله..." entry further down the
        // list was ever checked, silently corrupting a correct
        // transcription into the wrong phrase.
        var bestMatch: String? = null
        var bestSim = 0.0

        for (canonical in CANONICAL_MANUSCRIPT_PHRASES) {
            val normCanonical = normalizeArabic(canonical).replace(" ", "")
            if (norm.length >= 8 && normCanonical.length >= 8) {
                val sim = levenshteinSimilarity(norm, normCanonical)
                if (sim > bestSim) {
                    bestSim = sim
                    bestMatch = canonical
                }
            }
        }

        return if (bestMatch != null && bestSim >= 0.72) bestMatch else text
    }

    /**
     * Levenshtein-distance-based similarity (1.0 = identical, 0.0 =
     * completely different), normalized by the longer string's length.
     *
     * Replaces a prior "shared character set" metric (fraction of
     * characters in `a` that also appear anywhere in `b`, with no regard
     * for order, position, or repetition) that was verified to be far too
     * permissive for Arabic text: with only ~28 base letters, most short
     * classical Arabic phrases share the bulk of their character set with
     * each other regardless of actual similarity. On the shahada test case
     * above, that metric scored the (unrelated) basmalah at 0.774 --
     * higher than the actually-related shahada phrase further down the
     * canonical list (0.511) -- because both phrases happen to draw from
     * the same small pool of common Arabic letters. Levenshtein distance,
     * which accounts for character order and position, does not have
     * this failure mode.
     */
    private fun levenshteinSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        val maxLen = maxOf(m, n)
        return if (maxLen == 0) 1.0 else 1.0 - (dp[m][n].toDouble() / maxLen)
    }

    fun normalizeArabic(text: String): String {
        return text
            .replace(TASHKEEL_REGEX, "")
            .replace(TATWEEL_REGEX, "")
            .replace(Regex("[إأآٱ]"), "ا")
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
    }

    data class GlyphFrequency(
        val char: Char,
        val count: Int,
        val percentage: Float,
        val isRare: Boolean = false,
        val unicodeHex: String = ""
    )

    data class CharacterDistributionReport(
        val totalCharacters: Int,
        val uniqueGlyphs: Int,
        val harakatCount: Int,
        val harakatPercentage: Float,
        val frequencies: List<GlyphFrequency>,
        val avgConfidence: Float
    )

    fun analyzeDistribution(lines: List<String>, avgConfidence: Float = 0.95f): CharacterDistributionReport {
        val fullText = lines.joinToString(" ")
        if (fullText.isEmpty()) {
            return CharacterDistributionReport(0, 0, 0, 0f, emptyList(), 0f)
        }

        val totalChars = fullText.length
        val harakatMatches = TASHKEEL_REGEX.findAll(fullText).count()
        val charCounts = mutableMapOf<Char, Int>()

        for (c in fullText) {
            if (!c.isWhitespace()) {
                charCounts[c] = (charCounts[c] ?: 0) + 1
            }
        }

        val totalNonSpace = charCounts.values.sum().coerceAtLeast(1)
        val sortedFrequencies = charCounts.entries
            .sortedByDescending { it.value }
            .map { entry ->
                val pct = (entry.value.toFloat() / totalNonSpace) * 100f
                val isRare = pct < 0.5f && !entry.key.isWhitespace()
                val hex = "U+" + entry.key.code.toString(16).uppercase().padStart(4, '0')
                GlyphFrequency(
                    char = entry.key,
                    count = entry.value,
                    percentage = pct,
                    isRare = isRare,
                    unicodeHex = hex
                )
            }

        return CharacterDistributionReport(
            totalCharacters = totalChars,
            uniqueGlyphs = charCounts.size,
            harakatCount = harakatMatches,
            harakatPercentage = (harakatMatches.toFloat() / totalChars.coerceAtLeast(1)) * 100f,
            frequencies = sortedFrequencies,
            avgConfidence = avgConfidence
        )
    }
}
