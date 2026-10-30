package com.raviga.app.data.screening

/**
 * On-device check (layer 0) run before any input is saved: secrets and ID
 * numbers a client should never send us. The server re-checks and is the
 * authority; this only gives the client the chance to take them out first.
 * Kept in step with the iOS app's patterns and copy.
 */
object SensitiveScan {

    enum class Kind(val phrase: String) {
        PRIVATE_KEY("a private key"),
        API_KEY("an API key or token"),
        PASSWORD("a password"),
        CARD("a card number"),
        AADHAAR("an Aadhaar number"),
        PAN("a PAN"),
    }

    /** [range] is what gets replaced: for a password, only the value after the label. */
    data class Finding(val kind: Kind, val range: IntRange)

    const val REPLACEMENT = "[removed]"
    const val MESSAGE = "We never need passwords, keys or ID numbers to build your project, and it's safer not to share them."

    // Patterns follow the server's L1 detector (Raviga Backend, screening/secrets.py) so what
    // the phone flags is what the server would remove; iOS uses the same list.
    private val privateKey = Regex("-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----[\\s\\S]*?-----END (?:[A-Z0-9]+ )*PRIVATE KEY-----")
    /** A BEGIN line with no END (half a key pasted): the header and the base64 lines after it. */
    private val privateKeyOpen = Regex("-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----(?:\\s*[A-Za-z0-9+/=]{16,})*")
    /** aws_secret_access_key = <40 chars>: only the value. */
    private val awsSecret = Regex("(?i)\\baws_?secret_?(?:access_?)?key\\b\\s*[:=]\\s*[\"']?([A-Za-z0-9/+=]{40})(?![A-Za-z0-9/+=])")
    private val apiKeys = listOf(
        Regex("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b"),
        Regex("\\bgh[pousr]_[A-Za-z0-9]{36,}\\b"),
        Regex("\\bgithub_pat_[A-Za-z0-9_]{22,}"),
        // Claude API keys (contract v0.9.4): "sk-ant-api03-…", "sk-ant-usr-…", then a long base64url tail.
        Regex("\\bsk-ant-[A-Za-z0-9_-]{20,}"),
        // OpenAI keys contain a digit, so "sk-learn-style" prose is left alone.
        Regex("\\bsk-(?:proj-|svcacct-|admin-)?(?=[A-Za-z0-9_-]*\\d)[A-Za-z0-9_-]{20,}"),
        Regex("\\b[sr]k_live_[A-Za-z0-9]{16,}\\b"),
        Regex("\\brzp_(?:live|test)_[A-Za-z0-9]{14,}\\b"),
        Regex("\\bxox[abprs]-[A-Za-z0-9-]{10,}"),
        Regex("\\bAIza[0-9A-Za-z_-]{35}(?![0-9A-Za-z_-])"),
    )
    /**
     * "password: hunter2", "UPI PIN = 1234", "Key Secret: …". Only the value, and only when it
     * looks like a secret ([looksSecret]), so "password: minimum 8 characters" is left alone.
     */
    private val labelled = Regex(
        "(?i)\\b(passwords?|passwd|pwd|passcode|m?pin|upi\\s+pin|atm\\s+pin|secret|token|api[\\s_-]?key" +
            "|(?:key|client)[\\s_-]?secret|secret[\\s_-]?key)" +
            "\\s*(?:is\\s*)?[:=]\\s*([^\\s,;]+)",
    )
    /** Never part of a longer grouped number. */
    private val card = Regex("(?<![\\d+])(?<!\\d[ -])[2-6]\\d(?:[ -]?\\d){11,17}(?![ -]?\\d)")
    private val aadhaar = Regex("(?<![\\d+])(?<!\\d[ -])[2-9]\\d{3}(?:([ -]?)\\d{4}\\1\\d{4})(?![ -]?\\d)")
    private val mobileWithCountryCode = Regex("91[6-9]\\d{9}")
    /** The fourth letter is the holder type (P person, C company, …). */
    private val pan = Regex("\\b[A-Z]{3}[ABCFGHJLPT][A-Z]\\d{4}[A-Z]\\b")

    fun scan(text: String): List<Finding> {
        val found = mutableListOf<Finding>()
        privateKey.findAll(text).forEach { found += Finding(Kind.PRIVATE_KEY, it.range) }
        privateKeyOpen.findAll(text).forEach { found += Finding(Kind.PRIVATE_KEY, it.range) }
        awsSecret.findAll(text).forEach { m -> m.groups[1]?.let { found += Finding(Kind.API_KEY, it.range) } }
        apiKeys.forEach { r -> r.findAll(text).forEach { found += Finding(Kind.API_KEY, it.range) } }
        labelled.findAll(text).forEach { m ->
            val value = m.groups[2] ?: return@forEach
            if (looksSecret(value.value, m.groupValues[1])) found += Finding(Kind.PASSWORD, value.range)
        }
        card.findAll(text).forEach { m ->
            val digits = m.value.filter(Char::isDigit)
            if (digits.length in 13..19 && luhn(digits)) found += Finding(Kind.CARD, m.range)
        }
        aadhaar.findAll(text).forEach { m ->
            val digits = m.value.filter(Char::isDigit)
            // 91 and a mobile number is a phone number with its country code.
            if (!mobileWithCountryCode.matches(digits) && verhoeff(digits)) found += Finding(Kind.AADHAAR, m.range)
        }
        pan.findAll(text).forEach { found += Finding(Kind.PAN, it.range) }
        // Earlier kinds win where matches overlap (a key inside "api key: …", an Aadhaar-shaped run in a card).
        val kept = mutableListOf<Finding>()
        for (f in found) {
            if (kept.none { it.range.first <= f.range.last && f.range.first <= it.range.last }) kept += f
        }
        return kept.sortedBy { it.range.first }
    }

    /** A bare "PIN: 560001" is a postal code; "token: 5 per day" isn't a token. */
    private fun looksSecret(raw: String, label: String): Boolean {
        val value = raw.trim('"', '\'', '.', ')')
        if (value.length < 4 || value == REPLACEMENT) return false
        val l = label.lowercase()
        if (l == "pin") return value.matches(Regex("\\d{4}"))
        if (l.endsWith("pin")) return value.matches(Regex("\\d{4,6}"))
        val hasDigit = value.any(Char::isDigit)
        val hasSymbol = value.any { !it.isLetterOrDigit() }
        val mixedCase = value.drop(1).any(Char::isUpperCase) && value.any(Char::isLowerCase)
        return hasDigit || hasSymbol || mixedCase
    }

    fun redact(text: String, findings: List<Finding> = scan(text)): String {
        val sb = StringBuilder(text)
        findings.sortedByDescending { it.range.first }.forEach { sb.replace(it.range.first, it.range.last + 1, REPLACEMENT) }
        return sb.toString()
    }

    /** "This looks like it includes a card number and an API key or token." */
    fun title(findings: List<Finding>): String {
        val kinds = findings.map { it.kind }.distinct().map { it.phrase }
        val list = when (kinds.size) {
            0 -> "something private"
            1 -> kinds[0]
            2 -> "${kinds[0]} and ${kinds[1]}"
            else -> "${kinds[0]}, ${kinds[1]} and other secrets"
        }
        return "This looks like it includes $list."
    }

    internal fun luhn(digits: String): Boolean {
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }

    private val vD = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6), intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8), intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2), intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4), intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
    )
    private val vP = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2), intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0), intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5), intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8),
    )

    internal fun verhoeff(digits: String): Boolean {
        if (digits.length != 12) return false
        var c = 0
        digits.reversed().forEachIndexed { i, ch -> c = vD[c][vP[i % 8][ch - '0']] }
        return c == 0
    }
}
