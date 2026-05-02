package com.fynnix.app.features.categories.domain

/**
 * Default merchant-keyword taxonomy for auto-categorisation.
 *
 * This is the single source of truth consumed by both [DatabaseSeeder][com.fynnix.app.features.categories.data.DatabaseSeeder]
 * (which turns it into DB-editable auto-categorise rules) and [ChainedCategoryClassifier]'s
 * keyword-fallback layer (which covers the narrow window before seeding completes on a
 * fresh install). Keeping one list means the two layers can never drift out of sync, and
 * lets the seeder depend on plain category names/keywords instead of a specific
 * [CategoryClassifier] implementation.
 */
object CategoryKeywords {
    data class Group(val categoryName: String, val keywords: List<String>)

    val GROUPS: List<Group> = listOf(
        Group("Food", listOf("swiggy", "zomato", "food", "restaurant", "starbucks", "cafe", "blinkit", "zepto", "instamart", "dunzo", "biryani", "pizza", "burger")),
        Group("Transport", listOf("uber", "ola", "rapido", "fuel", "petrol", "metro", "irctc", "redbus", "makemytrip", "indigo", "airasia", "spicejet", "vistara", "goibibo")),
        Group("Shopping", listOf("amazon", "flipkart", "myntra", "shopping", "ajio", "meesho", "nykaa", "tata cliq", "reliance", "decathlon", "ikea")),
        Group("Bills", listOf("electric", "bill", "recharge", "broadband", "gas", "bsnl", "jio", "airtel", "vi", "vodafone", "idea", "dtv", "tata sky", "water", "postpaid")),
        Group("Entertainment", listOf("netflix", "spotify", "hotstar", "prime", "youtube", "zee5", "sonyliv", "bookmyshow", "pvr", "inox", "apple", "google play")),
        Group("Health", listOf("pharmacy", "hospital", "clinic", "medplus", "apollo", "netmeds", "tata 1mg", "practo", "lab", "diagnostic")),
        Group("Income", listOf("salary", "payroll", "interest", "dividend", "refund", "cashback")),
    )

    /**
     * Keywords at or under this length are prone to false-positive substring collisions
     * (e.g. "bill" inside "billdesk", "lab" inside an unrelated brand name) and must match
     * on a word boundary rather than as a raw substring.
     */
    const val SHORT_KEYWORD_LENGTH = 4

    fun matches(text: String, keyword: String): Boolean =
        if (keyword.length > SHORT_KEYWORD_LENGTH) text.contains(keyword)
        else Regex("\\b${Regex.escape(keyword.trim())}\\b").containsMatchIn(text)
}
