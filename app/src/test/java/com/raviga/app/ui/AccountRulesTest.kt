package com.raviga.app.ui

import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.DeviceSummary
import com.raviga.app.data.api.DevicesResponse
import com.raviga.app.data.api.Me
import com.raviga.app.data.api.RecoverByEmailConfirmRequest
import com.raviga.app.data.api.RecoveryEmail
import com.raviga.app.data.api.RegisterDeviceRequest
import com.raviga.app.ui.account.OtpAttempts
import com.raviga.app.ui.account.OtpAttempts.Outcome
import com.raviga.app.ui.account.PhoneNames
import com.raviga.app.ui.account.RecoveryEmailLogic
import com.raviga.app.ui.account.RecoveryEmailPrompt
import com.raviga.app.ui.account.RecoveryKeyFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R-079: recovery email, link codes and phones with access, with iOS's rules and words. */
class AccountRulesTest {

    private fun wrongCode() = ApiException(ApiException.OTP_INVALID, 400, "otp")

    @Test fun fiveWrongCodesAskForANewOne() {
        var attempts = OtpAttempts()
        repeat(4) {
            val (next, outcome) = attempts.failed(wrongCode())
            assertEquals(Outcome.TryAgain("That code didn't match. Try again."), outcome)
            attempts = next
        }
        val (fifth, outcome) = attempts.failed(wrongCode())
        assertEquals(Outcome.NewCode("Too many tries. Ask for a new code."), outcome)
        assertEquals(5, fifth.wrong)
    }

    @Test fun rateLimitedAsksForANewCodeAtOnce() {
        val (next, outcome) = OtpAttempts().failed(ApiException(ApiException.RATE_LIMITED, 429, "slow"))
        assertEquals(Outcome.NewCode(OtpAttempts.NEW_CODE), outcome)
        assertEquals(OtpAttempts.LIMIT, next.wrong)
    }

    @Test fun otherErrorsAreNotCountedAgainstTheCode() {
        val start = OtpAttempts(2)
        val (next, outcome) = start.failed(ApiException(ApiException.NETWORK, 0, "offline"))
        assertEquals(Outcome.Other, outcome)
        assertEquals(start, next)
        assertEquals(Outcome.Other, OtpAttempts().failed(IllegalStateException()).second)
    }

    @Test fun anEmailNeedsAnAtAndADotAfterIt() {
        assertTrue(RecoveryEmailLogic.looksLikeEmail("me@example.com"))
        assertTrue(RecoveryEmailLogic.looksLikeEmail("  me@example.co.in "))
        assertFalse(RecoveryEmailLogic.looksLikeEmail("me@example"))
        assertFalse(RecoveryEmailLogic.looksLikeEmail("@example.com"))
        assertFalse(RecoveryEmailLogic.looksLikeEmail("me example@x.com"))
        assertFalse(RecoveryEmailLogic.looksLikeEmail(""))
        assertEquals("123456", RecoveryEmailLogic.digits("123 456"))
    }

    @Test fun theKeyIsMaskedButKeepsItsShape() {
        assertEquals("rark_••••-••••", RecoveryKeyFormat.mask("rark_AB12-CD34"))
        assertEquals("••-••", RecoveryKeyFormat.mask("ab-cd"))
    }

    @Test fun protectYourCreditsOnlyOnceAndOnlyWithoutAConfirmedEmail() {
        val none = Me(clientId = "cl_1")
        val pending = none.copy(recoveryEmail = RecoveryEmail("me@example.com", verified = false))
        val confirmed = none.copy(recoveryEmail = RecoveryEmail("me@example.com", verified = true))
        assertTrue(RecoveryEmailPrompt.shouldOffer(none, alreadyShown = false))
        assertTrue(RecoveryEmailPrompt.shouldOffer(pending, alreadyShown = false))
        assertFalse(RecoveryEmailPrompt.shouldOffer(confirmed, alreadyShown = false))
        assertFalse(RecoveryEmailPrompt.shouldOffer(none, alreadyShown = true))
    }

    @Test fun phonesAreNamedAsOnIos() {
        assertEquals("This phone", PhoneNames.of(DeviceSummary("dv_1", "android", "GM1901", isCurrent = true)))
        assertEquals("GM1901", PhoneNames.of(DeviceSummary("dv_2", "android", "GM1901")))
        assertEquals("iPhone", PhoneNames.of(DeviceSummary("dv_3", "ios")))
        assertEquals("Android phone", PhoneNames.of(DeviceSummary("dv_4", "android", " ")))
    }

    // ----- Wire shapes (contract 3.1-3.3), with the app's Json settings -----

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }

    @Test fun devicesDecode() {
        val body = """{"devices":[{"deviceId":"dv_1","platform":"android","deviceModel":"GM1901","appVersion":"1.0.0",
            "createdAt":"2026-11-01T10:00:00Z","lastSeenAt":"2026-11-02T09:00:00Z","isCurrent":true},
            {"deviceId":"dv_2","platform":"ios","deviceModel":null,"appVersion":"1.0.0","createdAt":"2026-11-01T10:00:00Z",
            "lastSeenAt":null,"isCurrent":false}]}"""
        val devices = json.decodeFromString(DevicesResponse.serializer(), body).devices
        assertEquals(listOf("dv_1", "dv_2"), devices.map { it.deviceId })
        assertTrue(devices[0].isCurrent)
        assertEquals(null, devices[1].deviceModel)
    }

    @Test fun recoverByEmailSendsTheDeviceFields() {
        val body = json.encodeToJsonElement(
            RecoverByEmailConfirmRequest.serializer(),
            RecoverByEmailConfirmRequest("me@example.com", "123456", installId = "i", appVersion = "1.0", deviceModel = "GM1901", osVersion = "12"),
        ).jsonObject
        assertEquals(setOf("email", "otp", "platform", "installId", "appVersion", "deviceModel", "osVersion"), body.keys)
        assertEquals("android", body.getValue("platform").jsonPrimitive.content)
    }

    @Test fun registerCarriesTheLinkCodeOnlyWhenThereIsOne() {
        fun keys(link: String?) = json.encodeToJsonElement(
            RegisterDeviceRequest.serializer(),
            RegisterDeviceRequest(installId = "i", appVersion = "1.0", deviceModel = "m", osVersion = "12", linkCode = link),
        ).jsonObject.keys
        assertTrue("linkCode" in keys("ABCD-1234"))
        assertFalse("linkCode" in keys(null))
    }
}
