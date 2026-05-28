package com.household.app.data

class TransactionCategorizer {

    private val excludePatterns = listOf(
        "commerzbank", "ing-diba",
        "mittagstisch",
        "google payment ireland"
    )

    private val incomeKeywords = listOf(
        // German salary / wage keywords (strong signal)
        "gehalt", "gehaltszahlung", "gehaltsüberweisung",
        "lohn", "lohnzahlung", "lohnauszahlung",
        "vergütung", "arbeitsentgelt", "arbeitslohn",
        "bezüge", "bezu ge", "dienstbezüge",
        "entgelt", "monatsentgelt", "monatslohn", "monatsverdienst",
        // German benefits / social
        "rente", "rentenversicherung", "rentenanpassung",
        "arbeitsagentur", "bundesagentur für arbeit", "arbeitslosengeld",
        "familienkasse", "kindergeld", "elterngeld", "wohngeld",
        "kurzarbeitergeld", "sozialhilfe", "grundsicherung", "bürgergeld",
        "steuererstattung", "steuerrückzahlung", "finanzamt",
        "minijob", "minij", "werkstudent", "werkstudentenlohn",
        // English salary / payroll
        "salary", "payroll", "wages", "wage payment",
        "net pay", "gross pay", "payslip",
        // Employer indicators: companies paying salary often include GMBH/AG + SEPA
        "auszahlung",           // "Auszahlung Gehalt" is common in SEPA references
        // Named employers (user-specific, kept for backward compat)
        "caleda",               // Brian Benedict Caleda — mini-job at Flavor Academy
        "elektrobit"            // Primary employer
    )

    private val categoryKeywords = linkedMapOf(
        // Ulm-local (most specific, checked first)
        "Local Transit (SWU/DING)" to listOf(
            "swu nahverkehr", "swu mobil", "swu-app", "mein swu",
            "donau-iller-nahverkehr", "ding ticket"
        ),
        "Utilities (SWU)" to listOf(
            "swu energie", "swu stadtwerke", "swu teletext", "fernwaerme ulm", "fvu"
        ),
        "Municipal Fees & Public Kita" to listOf(
            "stadt ulm", "universitaetsstadt ulm", "buergerbuero ulm",
            "stadt neu-ulm", "landratsamt neu-ulm", "kita ulm", "stadtkasse ulm"
        ),
        "School Lunch / Mensa" to listOf(
            "studierendenwerk ulm", "stuwe ulm", "mensa uni ulm", "besseresser",
            "mensa", "menza", "schulessen", "cateringservice",
            "apetito", "sodexo", "verpflegung schule"
        ),
        "School Fees & Education" to listOf(
            "kepler-gymnasium", "humboldt-gymnasium", "schubart-gymnasium",
            "anna-essinger", "st. hildegard", "valckenburg", "list-schule",
            "schulgeld", "kita-gebühr", "kitagebuehr", "kindergartengebühr",
            "hortgebühr", "foerderverein", "förderverein", "kindertagesstätte"
        ),
        "Local Activities & Sports" to listOf(
            "ssv ulm", "ssv ulm 1846", "tsv pfuhl", "tsg soeflingen", "tsg söflingen",
            "sc buhlbronn", "schwimmkurs", "dlrg ulm", "vhs ulm", "musikschule ulm",
            "schwimmbad", "seepferdchen", "dlrg", "turnverein", "reitschule"
        ),
        "Transfers" to listOf(
            "sepa", "iban", "interbk", "interbank", "inter-bank", "bank to bank",
            "main account", "wise", "wise europe", "trwi", "ucc",
            "chithra", "siddharth", "akkinepalli", "ntbs",
            "n26", "revolut", "deutsche kreditbank"
        ),
        "Housing & Rent" to listOf(
            "miete", "kaltmiete", "warmmiete", "nebenkosten", "hausverwaltung",
            "gewobau", "kaution", "vonovia", "saga", "wohnungsbaugesellschaft",
            "wohnanlage", "mietnebenkosten"
        ),
        "Utilities" to listOf(
            "strom", "stadtwerke", "swu", "vattenfall", "e.on", "eon", "enbw",
            "lekker energie", "lekker energy",
            "mvv energie", "naturstrom", "badenova", "enercity",
            "wasser", "heizung", "waerme", "fernwaerme", "gasabschlag",
            "eprimo", "lichtblick", "yello strom"
        ),
        "Broadcasting License" to listOf(
            "rundfunkbeitrag", "gez", "beitragsservice", "ard zdf"
        ),
        "Groceries" to listOf(
            "rewe", "aldi", "lidl", "edeka", "penny", "netto", "kaufland",
            "alnatura", "denns", "tegut", "globus", "hit markt", "hit-markt",
            "norma", "famila", "bofrost", "e-center", "mix markt",
            "asia store", "asiamarkt", "asia markt",
            "trader joe", "whole foods", "supermarket", "foodmarket", "grocery", "costco"
        ),
        "Drugstore & Personal Care" to listOf(
            "dm-drogerie", "dm drogerie", "rossmann", "müller drogerie", "mueller drogerie",
            "budni", "ihr platz", "douglas", "notino", "parfuemerie"
        ),
        "Dining & Takeaway" to listOf(
            "restaurant", "cafe", "coffee", "pizza", "burger", "starbucks",
            "mcdonald", "kfc", "sushi", "doener", "döner", "nordsee",
            "lieferando", "wolt", "uber eats", "sodexo", "deliveroo",
            "bakery", "baeckerei", "backerei", "bäckerei", "konditorei",
            "staib", "menza", "mensa"
        ),
        "Long-Distance Transport" to listOf(
            "deutsche bahn", "deutschlandticket", "db vertri", "db nah",
            "bvg", "hvv", "mvg", "vvs", "ssb", "rhein main verkehr",
            "flixbus", "flixtrain", "fahrkarte", "bahnticket"
        ),
        "Gas & Fuel" to listOf(
            "aral", "shell", "totalenergies", "total ", "esso", "jet tankstelle",
            "omv", "tanken", "tankstelle", "agip", "q1 energie", "star tankstelle"
        ),
        "Carsharing & Taxi" to listOf(
            "miles", "share now", "sixt share", "sixt rent", "tier ", "lime ",
            "bolt", "uber", "taxi", "free now", "freenow", "flinkster", "cambio", "stadtmobil",
            "easypark", "logpay"
        ),
        "Travel" to listOf(
            "booking.com", "airbnb", "hotel", "hostel", "ryanair", "lufthansa",
            "easyjet", "wizz", "condor", "eurowings", "check24 flug",
            "parking", "toll", "mietwagen", "car rental", "conficar", "confitech",
            "bahncard", "bahn ", " db "
        ),
        "Discount & Department Stores" to listOf(
            "woolworth", "action ", "tedi ", "euroshop", "kik ", "tk maxx", "tkmaxx",
            "galeria", "kaufhof", "karstadt", "pepco"
        ),
        "Shopping & Clothing" to listOf(
            "amazon", "zalando", "otto ", "ebay", "kleinanzeigen", "temu", "shein",
            "h&m", "h+m", "c&a", "asos", "about you", "zara", "primark",
            "fashion", "online shop", "aliexpress", "decathlon", "sport scheck",
            "intersport", "peek und cloppenburg", "breuninger", "bestsecret"
        ),
        "Home & Furniture" to listOf(
            "ikea", "baur", "xxxlutz", "höffner", "hoeffner",
            "dänisches bettenlager", "jysk", "möbel", "moebel",
            "roller ", "weko", "poco domäne"
        ),
        "DIY & Hardware" to listOf(
            "obi ", "bauhaus", "hornbach", "toom", "hagebau",
            "baumarkt", "werkzeug", "hellweg"
        ),
        "Electronics" to listOf(
            "mediamarkt", "saturn ", "cyberport", "notebooksbilliger",
            "alternate", "apple store", "apple.com/bill", "gravis"
        ),
        "Media Subscriptions" to listOf(
            "netflix", "spotify", "amazon prime", "prime video", "disney plus",
            "disney+", "dazn", "audible", "apple.com", "youtuber premium",
            "sky ticket", "paramount", "crunchyroll", "maxdome"
        ),
        "Internet & Mobile" to listOf(
            "telekom", "vodafone", "o2 ", "telefonica", "freenet", "1&1",
            "congstar", "simyo", "blau mobil", "aldi talk", "mobilcom"
        ),
        "Insurance" to listOf(
            "allianz", "huk-coburg", "huk ", "ergo ", "axa ", "adac",
            "krankenkasse", "aok", "barmer", "tk techniker", "techniker krankenkasse",
            "dak", "bkk", "versicherung", "haftpflicht", "kfz versicherung",
            "gothaer", "generali", "zurich versicherung"
        ),
        "Pharmacy & Health" to listOf(
            "apotheke", "docmorris", "shop-apotheke", "aponeo", "sanitätshaus",
            "praxis", "arztpraxis", "zahnarzt", "physiotherapie", "optiker"
        ),
        "Bank Fees & Charges" to listOf(
            "kontoführungsgebühr", "kartenentgelt", "jahresgebühr karte",
            "zinsen", "dispozinsen", "überziehungszinsen",
            "bankgebühr", "verwaltungsgebühr konto"
        )
    )

    fun shouldExclude(description: String): Boolean {
        val lower = description.lowercase()
        return excludePatterns.any { lower.contains(it) }
    }

    fun isIncome(description: String): Boolean {
        val lower = description.lowercase()
        return incomeKeywords.any { lower.contains(it) }
    }

    /**
     * Returns a 0–100 salary confidence score for an incoming credit transaction.
     * >= 70 → auto-classify as Salary without user prompt.
     * 40–69 → show "Is this your salary?" confirmation.
     * < 40  → treat as generic Income.
     *
     * @param description raw booking description / payer name
     * @param amount      absolute (positive) credit amount
     */
    fun salaryConfidenceScore(description: String, amount: Double): Int {
        val lower = description.lowercase()
        var score = 0

        // Keyword signal (up to 50 pts)
        val strongKeywords = listOf(
            "gehalt", "gehaltszahlung", "lohn", "lohnzahlung", "vergütung",
            "arbeitsentgelt", "arbeitslohn", "entgelt", "salary", "payroll",
            "wages", "monatslohn", "monatsverdienst", "auszahlung"
        )
        val mediumKeywords = listOf(
            "bezüge", "dienstbezüge", "minijob", "werkstudent", "net pay", "gross pay"
        )
        score += when {
            strongKeywords.any { lower.contains(it) } -> 50
            mediumKeywords.any { lower.contains(it) }  -> 30
            else -> 0
        }

        // Amount signal (up to 30 pts) — below 300€ unlikely to be primary salary
        score += when {
            amount >= 2000 -> 30
            amount >= 800  -> 20
            amount >= 300  -> 10
            else           -> 0
        }

        // Corporate payer indicator (up to 20 pts)
        val corporateSuffixes = listOf(" gmbh", " ag", " kg", " ug", " e.g.", " gbr", " ltd", " inc")
        if (corporateSuffixes.any { lower.contains(it) }) score += 20

        return score.coerceIn(0, 100)
    }

    fun classifyCategory(description: String, currentCategory: String): String {
        val lower = description.lowercase()
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { lower.contains(it) }) {
                return category
            }
        }
        normalizeCategory(currentCategory)?.let { return it }
        return "Uncategorized"
    }

    fun getAllCategories(): List<String> = categoryKeywords.keys.toList()

    fun normalizeCategory(raw: String): String? {
        val lowered = raw.trim().lowercase()
        if (lowered.isBlank() || lowered == "other" || lowered == "uncategorized") return null
        return when {
            lowered.contains("grocer") || lowered.contains("supermarket") -> "Groceries"
            lowered == "grocery" -> "Groceries"
            lowered.contains("dining") || lowered.contains("eat out") || lowered.contains("restaurant") || lowered.contains("food") -> "Dining & Takeaway"
            lowered.contains("transport") || lowered.contains("public transit") -> "Public Transport"
            lowered.contains("travel") || lowered.contains("bahn") -> "Travel"
            lowered.contains("rent") || lowered.contains("housing") || lowered.contains("miete") -> "Housing & Rent"
            lowered.contains("utility") || lowered.contains("utilities") || lowered.contains("bill") -> "Utilities"
            lowered.contains("transfer") || lowered.contains("bank") -> "Transfers"
            lowered.contains("shop") || lowered.contains("clothing") -> "Shopping & Clothing"
            lowered.contains("drug") || lowered.contains("personal care") || lowered.contains("drogerie") -> "Drugstore & Personal Care"
            lowered.contains("subscription") || lowered.contains("media") || lowered.contains("streaming") -> "Media Subscriptions"
            lowered.contains("insurance") || lowered.contains("versicherung") -> "Insurance"
            lowered.contains("electronic") || lowered.contains("tech") -> "Electronics"
            lowered.contains("fuel") || lowered.contains("gas station") -> "Gas & Fuel"
            lowered.contains("furniture") || lowered.contains("home") -> "Home & Furniture"
            lowered.contains("diy") || lowered.contains("hardware") || lowered.contains("baumarkt") -> "DIY & Hardware"
            lowered.contains("internet") || lowered.contains("mobile") || lowered.contains("phone") -> "Internet & Mobile"
            lowered.contains("taxi") || lowered.contains("carsharing") -> "Carsharing & Taxi"
            lowered.contains("broadcasting") || lowered.contains("rundfunk") -> "Broadcasting License"
            lowered.contains("bank fee") || lowered.contains("gebühr") -> "Bank Fees"
            else -> null
        }
    }
}
