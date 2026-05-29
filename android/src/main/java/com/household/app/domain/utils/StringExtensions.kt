package com.household.app.domain.utils

import java.util.Locale

fun String.capitalizeWords(): String =
    split(" ").joinToString(" ") { word ->
        word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
