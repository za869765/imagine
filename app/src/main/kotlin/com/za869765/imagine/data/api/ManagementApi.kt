package com.za869765.imagine.data.api

import com.za869765.imagine.data.api.dto.InvoicePreviewResponse
import com.za869765.imagine.data.api.dto.PrepaidBalanceResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface ManagementApi {
    @GET("v1/billing/teams/{teamId}/prepaid/balance")
    suspend fun getPrepaidBalance(@Path("teamId") teamId: String): PrepaidBalanceResponse

    @GET("v1/billing/teams/{teamId}/postpaid/invoice/preview")
    suspend fun getInvoicePreview(@Path("teamId") teamId: String): InvoicePreviewResponse
}
