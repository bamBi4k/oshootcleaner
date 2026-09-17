package com.example.oshootcleaner

import java.util.Locale

/**
 * Central list of package-name patterns that identify apps a cache cleaner
 * should NOT touch automatically.
 *
 * Two reasons:
 *   1. Safety. Banking, 2FA, wallet, brokerage, and password-manager apps
 *      have their own integrity/anti-tamper checks. Even a harmless
 *      Settings screen transition can log the user out or trip fraud
 *      detection.
 *   2. Trust. Users get nervous when an automated tool starts opening
 *      their bank's settings. Even if we don't click anything destructive,
 *      the visual is enough to make them uninstall.
 *
 * The patterns are matched against the lowercased package name. They're
 * intentionally broad — false positives (skipping an app that would have
 * been fine) are cheap; false negatives (touching a bank or broker) are
 * not.
 */
object SensitivePackages {

    private val SENSITIVE_TOKENS = listOf(
        // -----------------------------------------------------------------
        // Banking (international + German-specific)
        // -----------------------------------------------------------------
        "bank", "banking", "sparkasse", "volksbank", "comdirect", "postbank",
        "dkb", "ingdiba", "targobank", "hypovereinsbank",
        "n26", "commerzbank", "deutschebank", "commerzbanking",
        "revolut", "bunq", "monzo",

        // -----------------------------------------------------------------
        // Brokerage, trading, investment, robo-advisors
        // (Trade Republic, Scalable, Bux, Robinhood, eToro, Degiro, etc.)
        // -----------------------------------------------------------------
        "traderepublic", "trade.republic", "traders",
        "scalable", "scalablecapital",
        "bux", "com.bux",
        "robinhood", "etoro", "degiro", "trading212", "trading.212",
        "broker", "brokerage", "trading", "invest",
        "wealth", "wealthfront", "betterment", "acorns", "m1finance",
        "etf", "portfolio",
        "coinbase", "binance", "kraken", "bitpanda", "bitstamp",
        "bitcoin", "ethereum", "crypto",

        // -----------------------------------------------------------------
        // Finance, payment, cards
        // -----------------------------------------------------------------
        "financ", "wallet", "payment", "paypal", "venmo", "cashapp",
        "stripe", "klarna", "sofort", "mollie", "adyen",
        "credit", "debit", "visa", "mastercard", "amex", "americanexpress",

        // -----------------------------------------------------------------
        // 2FA / authenticators
        // -----------------------------------------------------------------
        "authenticat", "otp", "totp", "2fa", "mfa", "authy", "duo",
        "microsoft.auth", "google.android.apps.authenticator",

        // -----------------------------------------------------------------
        // Password managers
        // -----------------------------------------------------------------
        "1password", "onepassword", "bitwarden", "lastpass", "dashlane",
        "keepass", "keeper", "nordpass", "protonpass",

        // -----------------------------------------------------------------
        // Secure messaging
        // -----------------------------------------------------------------
        "signal", "threema",

        // -----------------------------------------------------------------
        // Identity / KYC / government / tax
        // -----------------------------------------------------------------
        "identity", "kyc", "idauth", "postident",
        "elster", "bundesid", "ausweisapp",

        // -----------------------------------------------------------------
        // Specific large banks by full package fragment
        // -----------------------------------------------------------------
        "com.chase", "com.wf.wellsfargomobile", "com.bankofamerica",
        "com.citi", "com.capitalone", "com.discover", "com.americanexpress",
        "com.paypal", "com.squareup", "com.venmo"
    )

    /**
     * Optional hardcoded exact package names. Use this for apps whose
     * package name contains none of the tokens above but which the user
     * still considers sensitive. Keeping a separate list makes it easy to
     * review the "manual entries" without scrolling past pattern tokens.
     */
    private val EXPLICIT_SENSITIVE_PACKAGES = setOf(
        // Trade Republic's actual package — no token above matches it.
        "de.traderepublic.app",
        // Add any additional exact package names here as they're reported.
        // One per line, comma separated.
        "com.example.todo"
    )

    fun isSensitive(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase(Locale.ROOT)

        // Exact-match check first — cheap and catches hand-listed apps.
        if (lower in EXPLICIT_SENSITIVE_PACKAGES) return true

        // Then the token-based broad sweep.
        return SENSITIVE_TOKENS.any { lower.contains(it) }
    }
}