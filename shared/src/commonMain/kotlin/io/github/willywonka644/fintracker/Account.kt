package io.github.willywonka644.fintracker

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType {
    GIRO,
    CREDIT_CARD,
    SPARKONTO,
    TAGESGELD
}

/**
 * Repräsentiert ein Konto in der App.
 *
 * @property billingStartDay
 *  - Für GIRO: Starttag des Abrechnungszeitraums (z. B. 1–28)
 *  - Für CREDIT_CARD: Abrechnungstag der Karte, pro Karte einstellbar (1–28);
 *    fehlt der Wert, gilt 18 als Vorgabe
 *  - Für SPARKONTO/TAGESGELD: null (kein Abrechnungszeitraum relevant)
 */
@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val billingStartDay: Int? = null,
    val spendingLimit: Double? = null,
    val lastModifiedAt: Long = 0L,
    /** Sync tombstone: deleted accounts keep a row so the deletion wins over
     *  the other device's live copy in the last-write-wins merge. */
    val deleted: Boolean = false
)
