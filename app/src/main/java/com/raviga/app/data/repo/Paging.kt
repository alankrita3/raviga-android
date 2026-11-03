package com.raviga.app.data.repo

/**
 * R-094 P2-9 (iOS review 2): paging stops when the server answers the cursor it was sent (contract v0.9.7 says it
 * never does; a slip would otherwise list the same page again and again).
 */
object Paging {
    fun next(sent: String?, answered: String?): String? = answered?.takeIf { it.isNotBlank() && it != sent }
}
