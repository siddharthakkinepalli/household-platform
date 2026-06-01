package com.jugaad.feature.astro.data.db.converter

import androidx.room.TypeConverter

object AstroTypeConverters {

    @TypeConverter
    fun byteArrayToString(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    @TypeConverter
    fun stringToByteArray(value: String): ByteArray =
        android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
}
