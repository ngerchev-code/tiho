package com.ngerchev.tiho.speech

/** Small text helpers: spoken punctuation ("запетая" → ",") and joining phrases into a draft. */
object TextFormatter {

    private fun rule(words: String, out: String): Pair<Regex, String> =
        Regex(
            "\\s*(?<![\\p{L}\\p{N}])(?:$words)(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        ) to Regex.escapeReplacement(out)

    // Order matters: longer phrases first ("точка и запетая" before "точка").
    private val BG = listOf(
        rule("точка и запетая", ";"),
        rule("въпросителен знак|въпросителна", "?"),
        rule("удивителен знак|удивителна", "!"),
        rule("запетая", ","),
        rule("двоеточие", ":"),
        rule("нов ред|нов абзац", "\n"),
        rule("точка", ".")
    )

    private val EN = listOf(
        rule("question mark", "?"),
        rule("exclamation mark|exclamation point", "!"),
        rule("semicolon", ";"),
        rule("comma", ","),
        rule("colon", ":"),
        rule("new line|new paragraph", "\n"),
        rule("full stop|period", ".")
    )

    private val SPACE_AFTER_NEWLINE = Regex("\\n[ \\t]+")
    private val SENTENCE_START = Regex("([.!?]\\s+|\\n)(\\p{Ll})")

    fun applySpokenPunctuation(text: String, languageTag: String): String {
        var out = text
        val rules = if (languageTag.startsWith("bg")) BG else EN
        for ((regex, replacement) in rules) {
            out = regex.replace(out, replacement)
        }
        out = SPACE_AFTER_NEWLINE.replace(out, "\n")
        out = SENTENCE_START.replace(out) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }
        return out
    }

    /** Appends a recognized phrase to the draft with sensible spacing and capitalisation. */
    fun join(existing: String, addition: String): String {
        val add = addition.trim(' ')
        if (add.isEmpty()) return existing
        val base = existing.trimEnd(' ')
        val startsSentence = base.isEmpty() || base.last() in ".!?\n"
        val piece = if (startsSentence) add.replaceFirstChar { it.uppercase() } else add
        val separator = when {
            base.isEmpty() -> ""
            base.last() == '\n' -> ""
            piece.first() in ".,!?;:\n" -> ""
            else -> " "
        }
        return base + separator + piece
    }
}
