package com.raviga.app.data.api

import okhttp3.Interceptor
import okhttp3.Response

/** QA O7: `426 upgrade_required` on any call means this version is too old; the app shows its update screen. */
class UpgradeInterceptor(private val onUpgradeRequired: () -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 426) onUpgradeRequired()
        return response
    }
}

/** Version numbers as /config's minAppVersion gives them ("1.2.0"); a suffix such as "-debug" is ignored. */
object AppVersion {
    fun isBelow(app: String, minimum: String?): Boolean {
        if (minimum.isNullOrBlank()) return false
        val a = parts(app)
        val m = parts(minimum)
        for (i in 0 until maxOf(a.size, m.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = m.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.substringBefore('-').substringBefore('+').split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
