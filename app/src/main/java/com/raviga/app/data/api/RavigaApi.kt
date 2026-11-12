package com.raviga.app.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Raviga API (api-contract.md v0.6, local-first). Implemented by Retrofit against the
 * real backend and, in debug builds only, by the in-app demo backend ([DemoBackend]) when no
 * backend URL is configured. Non-2xx responses surface as [ApiException] via [apiCall].
 */
interface RavigaApi {

    @GET("health")
    suspend fun health(): HealthResponse

    @GET("config")
    suspend fun config(): AppConfig

    // Identity
    @POST("devices/register")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest): RegisterDeviceResponse

    @POST("devices/recover")
    suspend fun recoverDevice(@Body body: RecoverDeviceRequest): RegisterDeviceResponse

    @POST("me/recovery-key")
    suspend fun issueRecoveryKey(@Body body: Empty = Empty()): RecoveryKeyResponse

    @POST("me/link-codes")
    suspend fun createLinkCode(@Body body: Empty = Empty()): LinkCodeResponse

    // Recovery email and devices (contract 3.1-3.3)
    @PUT("me/recovery-email")
    suspend fun setRecoveryEmail(@Body body: RecoveryEmailRequest): PendingResponse

    @POST("me/recovery-email/confirm")
    suspend fun confirmRecoveryEmail(@Body body: OtpRequest): RecoveryEmailConfirmed

    @DELETE("me/recovery-email")
    suspend fun removeRecoveryEmail()

    @POST("devices/recover")
    suspend fun recoverByEmail(@Body body: RecoverByEmailRequest): Empty

    @POST("devices/recover/confirm")
    suspend fun recoverByEmailConfirm(@Body body: RecoverByEmailConfirmRequest): RegisterDeviceResponse

    @GET("me/devices")
    suspend fun devices(): DevicesResponse

    @DELETE("me/devices/{deviceId}")
    suspend fun revokeDevice(@Path("deviceId") deviceId: String)

    @GET("me")
    suspend fun me(): Me

    @PUT("me/push-token")
    suspend fun putPushToken(@Body body: PushTokenRequest)

    @DELETE("me/push-token")
    suspend fun deletePushToken()

    // Consent
    @GET("me/consent")
    suspend fun consent(): ConsentState

    @PUT("me/consent")
    suspend fun putConsent(@Body body: ConsentRequest): ConsentState

    // Stateless AI (v0.6): text in, brief out, nothing kept. All are jobs.
    @POST("ai/draft")
    suspend fun aiDraft(@Body body: AiDraftRequest): JobEnvelope

    @POST("ai/append")
    suspend fun aiAppend(@Body body: AiAppendRequest): JobEnvelope

    @POST("ai/regenerate")
    suspend fun aiRegenerate(@Body body: AiRegenerateRequest): JobEnvelope

    /** v0.16.0 (R-123): an id for a draft from before v0.16.0 (or one the server forgot), with no free AI edits. */
    @POST("briefs")
    suspend fun registerBrief(@Body body: Empty = Empty()): BriefRegistration

    /** v0.16.0: what's left on a brief, when its draft is reopened. */
    @GET("briefs/{briefId}/ai-edits")
    suspend fun aiEdits(@Path("briefId") briefId: String): AiEditsCount

    @POST("quotes")
    suspend fun createQuote(@Body body: QuoteRequest): JobEnvelope

    // Projects: they exist on the server only from submit on.
    @GET("projects")
    suspend fun projects(@Query("before") before: String? = null, @Query("limit") limit: Int = 50): ProjectsResponse

    @POST("projects")
    suspend fun submitProject(@Header("Idempotency-Key") idempotencyKey: String, @Body body: SubmitProjectRequest): Project

    @GET("projects/{id}")
    suspend fun project(@Path("id") id: String): Project

    @POST("projects/{id}/cancel")
    suspend fun cancelProject(@Path("id") id: String, @Body body: Empty = Empty()): Project

    /** v0.10.0 (R-103): pay an accepted project; body empty, or a new quote's token after the price expired. */
    @POST("projects/{id}/pay")
    suspend fun pay(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String, @Body body: PayRequest): Project

    // Delivery
    @POST("projects/{id}/accept")
    suspend fun accept(@Path("id") id: String, @Body body: Empty = Empty()): Project

    /** v0.10.0 (R-103): "Something's wrong" with a delivery. */
    @POST("projects/{id}/report")
    suspend fun report(@Path("id") id: String, @Body body: ReportRequest): Project

    /** v0.12.0 (R-109): a change request; the AI briefs and prices it as a job (type `change`). */
    @POST("projects/{id}/changes")
    suspend fun requestChange(@Path("id") id: String, @Body body: ChangeRequest): ChangeStarted

    @POST("projects/{id}/changes/{changeId}/pay")
    suspend fun payChange(
        @Path("id") id: String,
        @Path("changeId") changeId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: Empty = Empty(),
    ): Project

    /** v0.12.0: the client invited Raviga's publishing user in that store. */
    @POST("projects/{id}/handover/invited")
    suspend fun handoverInvited(@Path("id") id: String, @Body body: HandoverInvitedRequest): Project

    // Store-account emails (v0.12.0, R-109): where the TestFlight and Play test invites go.
    @GET("me/store-emails")
    suspend fun storeEmails(): StoreEmails

    @PUT("me/store-emails")
    suspend fun putStoreEmails(@Body body: StoreEmails): StoreEmails

    // Delivery targets
    @GET("me/delivery-targets")
    suspend fun deliveryTargets(): DeliveryTargets

    @PUT("me/delivery-targets")
    suspend fun putDeliveryTargets(@Body body: DeliveryTargetsRequest): DeliveryTargets

    /** Same route with explicit nulls, used to clear a target (explicitNulls is off globally). */
    @PUT("me/delivery-targets")
    suspend fun putDeliveryTargetsRaw(@Body body: kotlinx.serialization.json.JsonObject): DeliveryTargets

    @GET("me/aws/connect")
    suspend fun awsConnect(): AwsConnectInfo

    @POST("me/aws/verify")
    suspend fun awsVerify(@Body body: Empty = Empty()): AwsTarget

    // Credits
    @GET("me/credits")
    suspend fun credits(@Query("before") before: String? = null, @Query("limit") limit: Int = 50): CreditsResponse

    @POST("me/credits/sync")
    suspend fun syncCredits(@Header("Idempotency-Key") idempotencyKey: String, @Body body: Empty = Empty()): SyncResponse

    // Privacy
    @POST("me/export")
    suspend fun exportData(@Body body: Empty = Empty()): JobEnvelope

    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteMe(@Body body: DeleteMeRequest = DeleteMeRequest()): DeleteMeResponse

    // Jobs
    @GET("jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): Job
}
