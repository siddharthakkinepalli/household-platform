package com.household.app.domain.services

import com.household.app.domain.models.PantryCategory
import java.util.Locale

/**
 * Regex-based parser for German grocery receipts (REWE, Lidl, Kaufland, Aldi).
 * Handles comma-decimal prices (1,29), weight lines, Pfand, and merchant prefixes.
 */
object ReceiptItemParser {

    data class ParsedItem(
        val name: String,
        val category: PantryCategory,
        val quantity: Float = 1f
    )

    // ── Skip Patterns ─────────────────────────────────────────────────────────

    private val SKIP_PATTERNS = listOf(
        Regex("""(?i)\bpfand\b"""),
        Regex("""(?i)\b(tasche|tüte|beutel)\b"""),
        Regex("""(?i)\b(rabatt|coupon|guthaben|bon-nr|bonnr)\b"""),
        Regex("""(?i)\b(summe|gesamt|total|zwischensumme|gesamtbetrag)\b"""),
        Regex("""(?i)\b(mwst|mehrwertsteuer|ust|steuer)\b"""),
        Regex("""(?i)\b(datum|uhrzeit|kassierer|kasse\s*nr|filiale|markt\s*nr)\b"""),
        Regex("""(?i)(vielen\s+dank|auf\s+wiedersehen|herzlich\s+willkommen)"""),
        Regex("""(?i)\b(ec[\-\s]?karte|mastercard|visa|zahlung|gegeben|zurück|wechselgeld|kartenzahlung)\b"""),
        Regex("""(?i)^\s*(artikel|menge|preis|betrag)\s*$"""),
        Regex("""(?i)\bbar\b.*\d+[,.]\d{2}"""),
    )

    // Weight-based pricing: "0,452 kg x 1,29 EUR/kg"
    private val WEIGHT_PRICING_LINE =
        Regex("""^\s*\d+[,.]\d+\s*kg\s*[x×]\s*\d+[,.]\d+""", RegexOption.IGNORE_CASE)

    // Pure price lines: "1,49 A" or "12,99" or "-0,50 B"
    private val PURE_PRICE_LINE =
        Regex("""^\s*[+-]?\d{1,4}[,.]\d{2}\s*[AB*]?\s*$""")

    // Pure number / barcode / receipt metadata
    private val PURE_DIGITS_LINE =
        Regex("""^\s*[\d\s\-*./]+\s*$""")

    // Item line ending with a German-format price: "Vollmilch 3,5%       1,29 A"
    // Uses greedy capture so the name gets everything before the trailing price.
    private val ITEM_LINE_WITH_PRICE =
        Regex("""^(.+?)\s{2,}([+-]?\d{1,4}[,.]\d{2})\s*[AB*]?\s*$""")

    // Fallback: any line ending in a price (single space separator allowed)
    private val LINE_ENDS_WITH_PRICE =
        Regex("""^(.*\S)\s+([+-]?\d{1,4}[,.]\d{2})\s*[AB*]?\s*$""")

    // Quantity prefix: "2 x Joghurt" or "3x Milch"
    private val QUANTITY_PREFIX =
        Regex("""^(\d+)\s*[x×]\s+(.+)$""", RegexOption.IGNORE_CASE)

    // ── Merchant Prefix Cleaners ───────────────────────────────────────────────

    private val BRAND_PREFIXES = listOf(
        // 6-digit product codes (ALDI, NORMA, etc.)
        Regex("""^\d{6}\s+"""),
        // Brand prefixes
        Regex("""^BIO\s+""", RegexOption.IGNORE_CASE),
        Regex("""^REWE\s+BESTE\s+WAHL\s+""", RegexOption.IGNORE_CASE),
        Regex("""^REWE\s+""", RegexOption.IGNORE_CASE),
        Regex("""^K-CLASSIC\s+""", RegexOption.IGNORE_CASE),
        Regex("""^K\s+CLASSIC\s+""", RegexOption.IGNORE_CASE),
        Regex("""^JEDEN\s+TAG\s+""", RegexOption.IGNORE_CASE),
        Regex("""^JA[!]?\s+""", RegexOption.IGNORE_CASE),
        Regex("""^GUT\s*[&+]\s*GÜNSTIG\s+""", RegexOption.IGNORE_CASE),
        Regex("""^ALDI\s+""", RegexOption.IGNORE_CASE),
        Regex("""^LIDL\s+""", RegexOption.IGNORE_CASE),
        Regex("""^KAUFLAND\s+""", RegexOption.IGNORE_CASE),
        Regex("""^EDEKA\s+""", RegexOption.IGNORE_CASE),
        Regex("""^PENNY\s+""", RegexOption.IGNORE_CASE),
        Regex("""^NETTO\s+""", RegexOption.IGNORE_CASE),
        Regex("""^DENNS\s+""", RegexOption.IGNORE_CASE),
    )

    // ── Category Keyword Maps ─────────────────────────────────────────────────
    // Checked in priority order: FROZEN first (TK-Spinat → FROZEN, not PRODUCE)

    private val FROZEN_KWORDS = setOf(
        "tiefkühl", "tk-", " tk ", "speiseeis", "eiskrem", "eiscreme", "gefrier"
    )
    private val DAIRY_KWORDS = setOf(
        "milch", "käse", "joghurt", "yoghurt", "butter", "sahne", "quark",
        "mozzarella", "frischkäse", "schmelzkäse", "gruyere", "parmesan",
        "gouda", "cheddar", "emmentaler", "skyr", "kefir", "cremefine",
        "schmand", "crème fraîche", "creme fraiche", "frischkäs", "hüttenkäse",
        "ricotta", "mascarpone", "brie", "camembert", "eier"
    )
    private val GRAINS_KWORDS = setOf(
        "brot", "brötchen", "mehl", "nudel", "pasta", "spaghetti", "penne",
        "fusilli", "rigatoni", "lasagne", "reis", "haferflock", "müsli",
        "cornflake", "toast", "knäckebrot", "semmel", "croissant", "baguette",
        "laugenbrezel", "pretzel", "weizenmehl", "dinkelmehl", "grieß"
    )
    private val PRODUCE_KWORDS = setOf(
        "apfel", "banane", "tomate", "gurke", "kartoffel", "zwiebel", "salat",
        "karotte", "paprika", "gemüse", "spinat", "zucchini", "lauch",
        "brokkoli", "blumenkohl", "sellerie", "rote bete", "mango", "erdbeere",
        "himbeere", "kirsche", "pfirsich", "zitrone", "orange", "kiwi",
        "avocado", "feldsalat", "rucola", "kohlrabi", "radiesch", "pilze",
        "champignon", "fenchel", "rosenkohl", "erbsen", "bohnen", "linsen",
        "birne", "möhre", "möhren", "zwiebeln"
    )
    private val MEAT_KWORDS = setOf(
        "fleisch", "hähnchen", "hühnchen", "chicken", "wurst", "schinken",
        "hack", "steak", "schnitzel", "thunfisch", "lachs", "forelle",
        "garnele", "salami", "aufschnitt", "putenbrust", "rindfleisch",
        "schweinefleisch", "leberwurst", "blutwurst", "kassler", "speck",
        "fischfilet", "hering", "kabeljau", "tilapia", "fisch ",
        "rinder", "schwein", "pute", "truthahn", "ente", "lamm",
        "filet", "brust", "schenkel", "schoulder", "hackfleisch"
    )
    private val BEVERAGES_KWORDS = setOf(
        "wasser", "saft", " cola", "bier", "wein", "kaffee", "tee", "limo",
        "limonade", "sprudel", "eistee", "energydrink", "smoothie", "kakao",
        "mineralwasser", "apfelschorle", "orangensaft", "multivitamin",
        "schorle", "radler", "sekt", "prosecco", "nektar"
    )
    private val SNACKS_KWORDS = setOf(
        "chips", "keks", "schokolade", "gummibär", "nuss", "riegel",
        "cracker", "popcorn", "erdnuss", "mandel", "cashew", "müsliriegel",
        "plätzchen", "waffel", "bonbon", "lakritz", "snack", "salzstange",
        "brezel", "schokoriegel", "praline", "fruchtgummi", "haribo"
    )
    private val CONDIMENTS_KWORDS = setOf(
        " öl", "essig", "soße", "sauce", "ketchup", "senf", "mayonnaise",
        "mayo", " salz", "pfeffer", "zucker", "honig", "marmelade",
        "aufstrich", "pesto", "tomatenmark", "gewürz", "paprikapulver",
        "curry", "zimt", "vanille", "hefe", "backpulver", "natron",
        "speisestärke", "gelatine", "balsamico", "sojasauce", "tabasco"
    )
    private val HOUSEHOLD_KWORDS = setOf(
        "waschmittel", "spüli", "seife", "shampoo", "toilettenpapier",
        "küchenpapier", "müllbeutel", "reiniger", "schwamm", "frischhaltefolie",
        "alufolie", "backpapier", "deo", "zahnpasta", "duschgel", "conditioner",
        "spülmittel", "weichspüler", "putzmittel", "desinfek", "wc-", " wc "
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun parse(receiptText: String): List<ParsedItem> {
        val lines = receiptText.lines()
        val results = mutableListOf<ParsedItem>()
        val seen = mutableSetOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            if (shouldSkip(trimmed)) continue
            if (WEIGHT_PRICING_LINE.containsMatchIn(trimmed)) continue
            if (PURE_PRICE_LINE.matches(trimmed)) continue
            if (PURE_DIGITS_LINE.matches(trimmed)) continue

            val (rawName, qty) = extractNameAndQuantity(trimmed) ?: continue
            if (rawName.length < 2) continue

            val cleaned = applyBrandPrefixes(rawName)
            if (cleaned.length < 2) continue

            val key = cleaned.lowercase(Locale.GERMAN)
            if (!seen.add(key)) continue

            results += ParsedItem(
                name = cleaned,
                category = categorize(cleaned),
                quantity = qty
            )
        }

        return results
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private fun shouldSkip(line: String): Boolean =
        SKIP_PATTERNS.any { it.containsMatchIn(line) }

    private fun extractNameAndQuantity(line: String): Pair<String, Float>? {
        // Check for explicit quantity prefix: "2 x Joghurt ..."
        val qMatch = QUANTITY_PREFIX.find(line)
        val (workLine, qty) = if (qMatch != null) {
            qMatch.groupValues[2].trim() to qMatch.groupValues[1].toFloatOrNull()
        } else {
            line to null
        }

        val name = when {
            ITEM_LINE_WITH_PRICE.matches(workLine) ->
                ITEM_LINE_WITH_PRICE.find(workLine)?.groupValues?.getOrNull(1)?.trim()
            LINE_ENDS_WITH_PRICE.matches(workLine) ->
                LINE_ENDS_WITH_PRICE.find(workLine)?.groupValues?.getOrNull(1)?.trim()
            // No price found — accept only if line looks like a product name
            !workLine.any { it.isDigit() } || workLine.contains(Regex("""[a-zA-ZäöüÄÖÜß]{3,}""")) ->
                workLine.trim()
            else -> null
        } ?: return null

        if (name.isBlank() || name.length < 2) return null
        return name to (qty ?: 1f)
    }

    private fun applyBrandPrefixes(name: String): String {
        var result = name
        // Apply all prefix removals repeatedly until nothing changes
        var changed = true
        while (changed) {
            changed = false
            for (prefix in BRAND_PREFIXES) {
                val replaced = prefix.replaceFirst(result, "").trim()
                if (replaced != result) {
                    result = replaced
                    changed = true
                    break
                }
            }
        }
        return result.replaceFirstChar { it.titlecase(Locale.GERMAN) }
    }

    private fun categorize(name: String): PantryCategory {
        val lower = " ${name.lowercase(Locale.GERMAN)} "
        return when {
            FROZEN_KWORDS.any { lower.contains(it) }    -> PantryCategory.FROZEN
            DAIRY_KWORDS.any { lower.contains(it) }     -> PantryCategory.DAIRY
            GRAINS_KWORDS.any { lower.contains(it) }    -> PantryCategory.GRAINS
            PRODUCE_KWORDS.any { lower.contains(it) }   -> PantryCategory.PRODUCE
            MEAT_KWORDS.any { lower.contains(it) }      -> PantryCategory.MEAT_FISH
            BEVERAGES_KWORDS.any { lower.contains(it) } -> PantryCategory.BEVERAGES
            SNACKS_KWORDS.any { lower.contains(it) }    -> PantryCategory.SNACKS
            CONDIMENTS_KWORDS.any { lower.contains(it) }-> PantryCategory.CONDIMENTS
            HOUSEHOLD_KWORDS.any { lower.contains(it) } -> PantryCategory.HOUSEHOLD
            else                                         -> PantryCategory.OTHER
        }
    }
}
