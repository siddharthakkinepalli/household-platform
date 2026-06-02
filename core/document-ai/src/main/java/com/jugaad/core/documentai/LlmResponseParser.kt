package com.jugaad.core.documentai

import kotlinx.serialization.json.*

object LlmResponseParser {

    fun parse(llmOutput: String): Pair<Map<String, String>, Float> {
        return try {
            val start = llmOutput.indexOf('{')
            val end = llmOutput.lastIndexOf('}')
            if (start == -1 || end == -1 || end < start) return emptyMap<String, String>() to 0f

            val jsonString = llmOutput.substring(start, end + 1)
            val json = Json.parseToJsonElement(jsonString).jsonObject
            
            val result = mutableMapOf<String, String>()
            flatten(json, "", result)
            
            result to 1.0f
        } catch (e: Exception) {
            emptyMap<String, String>() to 0f
        }
    }

    private fun flatten(element: JsonElement, prefix: String, result: MutableMap<String, String>) {
        when (element) {
            is JsonObject -> {
                element.forEach { (key, value) ->
                    val newPrefix = if (prefix.isEmpty()) key else "${prefix}_$key"
                    flatten(value, newPrefix, result)
                }
            }
            is JsonArray -> {
                if (element.all { it is JsonPrimitive }) {
                    result[prefix] = element.joinToString(",") { it.jsonPrimitive.content }
                } else {
                    result[prefix] = element.toString()
                }
            }
            is JsonPrimitive -> {
                if (element !is JsonNull) {
                    result[prefix] = element.content
                }
            }
        }
    }
}
