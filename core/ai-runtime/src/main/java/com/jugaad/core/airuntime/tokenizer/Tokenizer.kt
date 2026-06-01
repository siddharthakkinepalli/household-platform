package com.jugaad.core.airuntime.tokenizer

import android.content.Context
import com.jugaad.core.airuntime.model.ModelConfig
import org.json.JSONObject

/**
 * Contract for prompt tokenizers used by [LocalInferenceEngine].
 *
 * Implementations must be thread-safe — [encode] and [decode] may be called
 * concurrently from [kotlinx.coroutines.Dispatchers.Default].
 */
interface Tokenizer {
    /** Converts a text string into a sequence of integer token IDs. */
    fun encode(text: String): IntArray

    /** Converts a sequence of token IDs back to a human-readable string. */
    fun decode(ids: IntArray): String

    /** Total vocabulary size (including special tokens). */
    val vocabSize: Int
}

/**
 * Byte Pair Encoding (BPE) tokenizer compatible with HuggingFace vocabulary format.
 *
 * Loads vocabulary from [ModelConfig.VOCAB_ASSET] and merge rules from [ModelConfig.MERGES_ASSET].
 *
 * Vocabulary JSON format (astro_vocab.json):
 *   {"<pad>": 0, "<eos>": 1, "<s>": 2, "<unk>": 3, "Ġthe": 4, ...}
 *
 * Merges format (astro_merges.txt), one merge per line:
 *   Ġ t
 *   h e
 *   th e
 *   ...
 *
 * Word boundary marker: 'Ġ' (U+0120) prepended to space-separated word tokens,
 * matching the GPT-2 / SentencePiece byte-level BPE convention.
 */
class BpeTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val mergeRank: Map<Long, Int>,
    override val vocabSize: Int
) : Tokenizer {

    private val idToToken: Array<String> = Array(vocabSize) { "" }.also { arr ->
        vocab.forEach { (token, id) -> if (id < arr.size) arr[id] = token }
    }

    override fun encode(text: String): IntArray {
        val ids = mutableListOf<Int>()

        // Split by special tokens first, then BPE encode the text parts.
        // For Qwen2.5, special tokens are <|im_start|>, <|im_end|>, <|endoftext|>.
        val specialTokens = listOf("<|im_start|>", "<|im_end|>", "<|endoftext|>")
        
        var remaining = text
        while (remaining.isNotEmpty()) {
            var foundSpecial = false
            for (special in specialTokens) {
                if (remaining.startsWith(special)) {
                    ids.add(vocab[special] ?: ModelConfig.BOS_ID)
                    remaining = remaining.substring(special.length)
                    foundSpecial = true
                    break
                }
            }
            if (foundSpecial) continue

            // Find next special token to process text before it
            var nextSpecialIdx = -1
            for (special in specialTokens) {
                val idx = remaining.indexOf(special)
                if (idx != -1 && (nextSpecialIdx == -1 || idx < nextSpecialIdx)) {
                    nextSpecialIdx = idx
                }
            }

            val textPart = if (nextSpecialIdx == -1) remaining else remaining.substring(0, nextSpecialIdx)
            encodeTextPart(textPart, ids)
            
            remaining = if (nextSpecialIdx == -1) "" else remaining.substring(nextSpecialIdx)
        }

        return ids.toIntArray()
    }

    private fun encodeTextPart(text: String, ids: MutableList<Int>) {
        val words = text.split(' ')
        words.forEachIndexed { wordIdx, word ->
            if (word.isEmpty()) return@forEachIndexed
            val marked = if (wordIdx == 0 && !text.startsWith(" ")) word else "Ġ$word"

            var symbols: MutableList<String> = marked.map { it.toString() }.toMutableList()
            while (symbols.size > 1) {
                var bestRank = Int.MAX_VALUE
                var bestIdx  = -1
                for (i in 0 until symbols.size - 1) {
                    val key  = pairKey(symbols[i], symbols[i + 1])
                    val rank = mergeRank[key] ?: Int.MAX_VALUE
                    if (rank < bestRank) { bestRank = rank; bestIdx = i }
                }
                if (bestIdx < 0 || bestRank == Int.MAX_VALUE) break
                val merged = symbols[bestIdx] + symbols[bestIdx + 1]
                symbols = (symbols.subList(0, bestIdx) + merged + symbols.subList(bestIdx + 2, symbols.size)).toMutableList()
            }

            symbols.forEach { token ->
                val id = vocab[token]
                if (id != null) {
                    ids += id
                } else {
                    token.toByteArray(Charsets.UTF_8).forEach { b ->
                        val byteToken = "<0x%02X>".format(b.toInt() and 0xFF)
                        ids += vocab[byteToken] ?: ModelConfig.BOS_ID
                    }
                }
            }
        }
    }

    override fun decode(ids: IntArray): String {
        val sb = StringBuilder()
        for (id in ids) {
            // Llama-3.2 special token IDs (128000–128009) are above the normal vocab range.
            // Skip BOS, EOS, EOT, PAD; decode byte-level tokens (<0xXX>) back to UTF-8.
            if (id == ModelConfig.BOS_ID || id == ModelConfig.EOS_ID ||
                id == ModelConfig.EOT_ID || id == ModelConfig.PAD_ID) continue
            val token = if (id in idToToken.indices) idToToken[id] else continue
            when {
                token.matches(Regex("<0x[0-9A-Fa-f]{2}>")) -> {
                    val byte = token.substring(3, 5).toInt(16).toByte()
                    sb.append(String(byteArrayOf(byte), Charsets.UTF_8))
                }
                else -> sb.append(token.replace('Ġ', ' '))
            }
        }
        return sb.toString().trimStart()
    }

    companion object {
        /**
         * Loads and constructs a [BpeTokenizer] from the assets bundled with [context].
         * Must be called from a non-main dispatcher (reads files synchronously).
         *
         * @throws IllegalStateException if vocab or merges files are missing or malformed.
         */
        fun fromAssets(context: Context): BpeTokenizer {
            // Load vocabulary
            val vocabJson = context.assets.open(ModelConfig.VOCAB_ASSET).bufferedReader().use { it.readText() }
            val jsonObj   = JSONObject(vocabJson)
            val vocab     = HashMap<String, Int>(jsonObj.length())
            val keys      = jsonObj.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                vocab[token] = jsonObj.getInt(token)
            }

            // Load merge rules — each line is "token_a token_b"
            val mergeRank = HashMap<Long, Int>()
            context.assets.open(ModelConfig.MERGES_ASSET).bufferedReader().useLines { lines ->
                var rank = 0
                for (line in lines) {
                    if (line.isBlank() || line.startsWith("#")) continue
                    val space = line.indexOf(' ')
                    if (space < 0) continue
                    val a = line.substring(0, space)
                    val b = line.substring(space + 1)
                    mergeRank[pairKey(a, b)] = rank++
                }
            }

            val vocabSize = (vocab.values.maxOrNull() ?: 0) + 1
            return BpeTokenizer(vocab, mergeRank, vocabSize)
        }

        // Packs two token-string hash codes into a Long for O(1) map lookup
        private fun pairKey(a: String, b: String): Long =
            (a.hashCode().toLong() shl 32) or (b.hashCode().toLong() and 0xFFFFFFFFL)
    }
}
