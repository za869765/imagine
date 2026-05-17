package com.za869765.imagine.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// xAI Management API wraps monetary values in {"val": "..."}.
// `val` is a Kotlin keyword so we expose it as `value` via SerialName.
@Serializable
data class AmountVal(
    @SerialName("val") val value: String? = null,
)

@Serializable
data class PrepaidBalanceResponse(
    val total: AmountVal? = null,
)

@Serializable
data class InvoicePreviewResponse(
    val coreInvoice: CoreInvoice? = null,
    val effectiveSpendingLimit: String? = null,
    val billingCycle: BillingCycle? = null,
)

@Serializable
data class CoreInvoice(
    val amountBeforeVat: String? = null,
    val amountAfterVat: String? = null,
    val vatCost: String? = null,
    val prepaidCredits: AmountVal? = null,
    val prepaidCreditsUsed: AmountVal? = null,
    val lines: List<InvoiceLine>? = null,
)

@Serializable
data class InvoiceLine(
    val description: String? = null,
    val unitType: String? = null,
    val unitPrice: String? = null,
    val numUnits: String? = null,
    val amount: String? = null,
)

@Serializable
data class BillingCycle(
    val year: Int? = null,
    val month: Int? = null,
)
