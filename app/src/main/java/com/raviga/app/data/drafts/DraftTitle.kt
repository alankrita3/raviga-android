package com.raviga.app.data.drafts

/**
 * R-082: a draft is named from its first note instead of "Untitled draft", on the phone only. Gemini Nano writes the
 * title where the phone has it ([NanoTitles]); everywhere else, and whenever the model gives nothing usable, the first
 * meaningful words of the note do. The same rules as iOS's DraftTitle.swift, case for case.
 */
object DraftTitle {
    /** The most of the first note either path reads. */
    const val INPUT_LIMIT = 1_000
    const val MAX_WORDS = 6
    const val MAX_CHARACTERS = 60

    /** Openers that say nothing about the project, dropped from the start (longest first, repeatedly). */
    val fillers: List<List<String>> = listOf(
        listOf("i", "would", "like", "to"), listOf("i", "am", "looking", "for"), listOf("we", "would", "like", "to"),
        listOf("i", "would", "like"), listOf("we", "would", "like"), listOf("i'd", "like", "to"), listOf("i", "want", "to"),
        listOf("i", "need", "to"), listOf("we", "want", "to"), listOf("we", "need", "to"), listOf("looking", "for"),
        listOf("i'd", "like"), listOf("i", "want"), listOf("i", "need"), listOf("we", "want"), listOf("we", "need"),
        listOf("can", "you"), listOf("could", "you"), listOf("help", "me"), listOf("build", "me"), listOf("make", "me"),
        listOf("please"), listOf("so"), listOf("okay"), listOf("ok"), listOf("um"), listOf("uh"), listOf("hi"),
        listOf("hello"), listOf("basically"),
    ).sortedByDescending { it.size }

    val articles: Set<String> = setOf("a", "an", "the", "some")

    /** Words a title shouldn't end on. */
    val danglers: Set<String> = setOf(
        "and", "or", "for", "with", "to", "of", "in", "on", "at", "that", "which", "who", "where", "the", "a", "an",
        "my", "our", "your", "their", "is", "are", "so", "but", "from", "by", "as",
    )

    /** The fallback: the first 3 to 6 meaningful words of the note's first sentence, in sentence case. Null when the note has no words. */
    fun fallback(note: String): String? {
        val text = note.take(INPUT_LIMIT)
        val sentence = text.split('.', '!', '?', '\n', '\r').firstOrNull { part -> part.any { it.isLetter() } }.orEmpty()
        var words = words(sentence).map { word -> word.trim { isTrimmedFromWords(it) } }.filter { it.isNotEmpty() }
        var trimming = true
        while (trimming) {
            trimming = false
            for (filler in fillers) {
                if (words.size > filler.size && words.take(filler.size).map { it.lowercase() } == filler) {
                    words = words.drop(filler.size)
                    trimming = true
                    break
                }
            }
        }
        if (words.size > 1 && words.first().lowercase() in articles) words = words.drop(1)
        words = words.take(MAX_WORDS)
        // A filler again ("I want a simple app I want to…", as speech can repeat) ends the title there.
        val restart = (1 until words.size).firstOrNull { index ->
            fillers.any { filler -> filler.size > 1 && words.drop(index).take(filler.size).map { it.lowercase() } == filler }
        }
        if (restart != null) words = words.take(restart)
        while (words.size > 1 && words.last().lowercase() in danglers) words = words.dropLast(1)
        return tidy(words.joinToString(" "))
    }

    /**
     * A title as either path gives it, made to the rules: no quotes, no trailing punctuation, at most 6 words and 60
     * characters, the first letter capitalised. Null when nothing usable is left (or the model wrote a sentence).
     */
    fun tidy(raw: String): String? {
        val firstLine = raw.split('\n', '\r', '\u000B', '\u000C', '\u0085', ' ', ' ').firstOrNull { it.isNotEmpty() }.orEmpty()
        val unquoted = firstLine.trim { it in "\"'“”‘’`*#" || it == ' ' || it == '\t' }
        val all = words(unquoted)
        if (all.isEmpty() || all.size > MAX_WORDS + 2) return null
        var title = all.take(MAX_WORDS).joinToString(" ")
        title = title.trimEnd { isPunctuation(it) || it.isWhitespace() }
        while (title.length > MAX_CHARACTERS && ' ' in title) title = title.substring(0, title.lastIndexOf(' '))
        if (title.none { it.isLetter() }) return null
        return title.replaceFirstChar { it.uppercase() }
    }

    /** iOS's instructions for the on-device model, word for word. */
    const val INSTRUCTIONS = "You name app and software project ideas. Reply with only a title of 3 to 6 words for the project the user " +
        "describes: plain words, in the same language as the description, no quotes, no trailing punctuation."

    private fun words(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (c in text) {
            if (c.isWhitespace()) {
                if (current.isNotEmpty()) { out += current.toString(); current.clear() }
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    /** Unicode punctuation (Swift's CharacterSet.punctuationCharacters). */
    private fun isPunctuation(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION,
        -> true
        else -> false
    }

    /** Punctuation is trimmed from the ends of each word, except the marks that belong in names (O'Brien, e-commerce, B&B). */
    private fun isTrimmedFromWords(c: Char): Boolean = c !in "'-&+" && isPunctuation(c)
}
