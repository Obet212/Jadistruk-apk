package com.example.engine

import java.text.Normalizer

object EscPosCharacterNormalizer {

    // Regex matching all bullet / censor dot symbols commonly used in banking apps (e.g. •••••••)
    private val BULLET_PATTERN = Regex("[•●·∙▪▫◦⦿⁃\\u2022\\u25CF\\u00B7\\u2219\\u25AA\\u25AB\\u25E6\\u29BF\\u2043]")

    /**
     * Normalizes text according to strict thermal printer / ESC-POS rules:
     * 1. Replaces sensor bullets with asterisks (*), strictly preserving the character count.
     * 2. Replaces unsupported Unicode and special chars with clean ASCII equivalents.
     * 3. Prevents '?' replacement during printing.
     */
    fun normalizeText(input: String?): String {
        if (input == null) return ""

        // 1. Convert all bullet censor characters to '*' with exact 1-to-1 length preservation
        var result = BULLET_PATTERN.replace(input, "*")

        // 2. Normalize smart quotes and dashes to standard ASCII
        result = result
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u2026', '.')
            .replace('–', '-')
            .replace('—', '-')

        // 3. Decompose accented characters (e.g. é -> e)
        result = Normalizer.normalize(result, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")

        // 4. Clean up any remaining non-printable / control chars (except newline and standard ASCII 32..126)
        val sb = StringBuilder()
        for (ch in result) {
            when {
                ch == '\n' || ch == '\r' || ch == '\t' -> sb.append(ch)
                ch.code in 32..126 -> sb.append(ch)
                ch == '€' -> sb.append("EUR")
                ch == '£' -> sb.append("GBP")
                ch == '¥' -> sb.append("JPY")
                ch == '₹' -> sb.append("INR")
                // If it's another non-ASCII char that would cause '?' on ESC/POS, map safely or skip
                else -> {
                    // Try to preserve if digit or letter in other scripts, otherwise space
                    if (ch.isLetterOrDigit()) {
                        sb.append(ch)
                    } else if (ch.isWhitespace()) {
                        sb.append(' ')
                    } else {
                        sb.append(' ')
                    }
                }
            }
        }

        return sb.toString()
    }

    /**
     * Formats a two-column line: Left label, Right value, padded with spaces or dots.
     * Line length: 32 chars for 58mm, 48 chars for 80mm.
     */
    fun formatTwoColumns(
        left: String,
        right: String,
        maxWidth: Int = 32,
        padChar: Char = ' '
    ): String {
        val cleanLeft = normalizeText(left)
        val cleanRight = normalizeText(right)

        val totalLength = cleanLeft.length + cleanRight.length + 1
        if (totalLength <= maxWidth) {
            val padCount = maxWidth - cleanLeft.length - cleanRight.length
            val padding = padChar.toString().repeat(padCount)
            return "$cleanLeft$padding$cleanRight"
        } else {
            // If too long, wrap or split into multiple lines
            val maxLeft = maxWidth - cleanRight.length - 1
            if (maxLeft > 5) {
                val truncatedLeft = cleanLeft.take(maxLeft)
                val padCount = maxWidth - truncatedLeft.length - cleanRight.length
                val padding = padChar.toString().repeat(padCount.coerceAtLeast(1))
                return "$truncatedLeft$padding$cleanRight"
            } else {
                return "$cleanLeft\n" + cleanRight.padStart(maxWidth, ' ')
            }
        }
    }

    /**
     * Centers text for header / footer.
     */
    fun centerText(text: String, maxWidth: Int = 32): String {
        val clean = normalizeText(text)
        if (clean.length >= maxWidth) return clean
        val leftPad = (maxWidth - clean.length) / 2
        val rightPad = maxWidth - clean.length - leftPad
        return " ".repeat(leftPad) + clean + " ".repeat(rightPad)
    }

    /**
     * Generates a dashed separator line.
     */
    fun makeDivider(maxWidth: Int = 32, char: Char = '-'): String {
        return char.toString().repeat(maxWidth)
    }
}
