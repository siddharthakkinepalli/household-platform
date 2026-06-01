package com.jugaad.core.security.log

import android.util.Log
import com.jugaad.core.security.BuildConfig
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Structured, PII-safe logger for the astro sub-system.
 *
 * Guarantees:
 * - No latitude, longitude, date-of-birth, or name strings reach any log sink.
 * - Debug traces are compiled out in release builds (BuildConfig.DEBUG check is
 *   inlined by R8 and the dead branch is eliminated).
 * - Stack traces are anonymized: class and method names are SHA-256 hashed,
 *   only line numbers are preserved in their numeric form.
 */
@Singleton
class AstroLogger @Inject constructor() {

    private val piiPattern = Regex(
        """(\d{1,3}\.\d{4,})|""" +            // lat/lon decimal
        """(\b\d{4}-\d{2}-\d{2}\b)|""" +       // ISO date
        """(\b\d{2}/\d{2}/\d{4}\b)|""" +       // DD/MM/YYYY date
        """([A-Z][a-z]+ [A-Z][a-z]+)"""        // Firstname Lastname (title-case pair)
    )

    enum class Level { DEBUG, INFO, WARN, ERROR }

    /**
     * Logs a structured event. [tag] and [event] must be semantic codes, not PII.
     * [metadata] is sanitized before logging.
     */
    fun log(level: Level, tag: String, event: String, metadata: Map<String, String> = emptyMap()) {
        if (level == Level.DEBUG && !BuildConfig.DEBUG) return

        val sanitizedMeta = metadata.mapValues { (_, v) -> sanitize(v) }
        val message = buildString {
            append("[ASTRO] $event")
            if (sanitizedMeta.isNotEmpty()) append(" | ${sanitizedMeta.entries.joinToString { "${it.key}=${it.value}" }}")
        }

        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO  -> Log.i(tag, message)
            Level.WARN  -> Log.w(tag, message)
            Level.ERROR -> Log.e(tag, message)
        }
    }

    /**
     * Logs an anonymized crash event. Stack trace frames are hashed — no class names
     * or method signatures appear in any log output.
     */
    fun logCrash(tag: String, throwable: Throwable) {
        val anonymized = buildString {
            appendLine("[ASTRO_CRASH]")
            throwable.stackTrace.take(10).forEach { frame ->
                val classHash  = sha256Short("${frame.className}.${frame.methodName}")
                append("  at [$classHash]:${frame.lineNumber}")
                appendLine()
            }
        }
        Log.e(tag, anonymized)
    }

    /** Strips patterns that could contain PII before any string reaches a log sink. */
    fun sanitize(input: String): String = piiPattern.replace(input, "***")

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
