package com.raviga.app.data.screening

import com.raviga.app.data.screening.SensitiveScan.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveScanTest {

    private fun kinds(text: String) = SensitiveScan.scan(text).map { it.kind }

    @Test fun ordinaryProjectTextIsClean() {
        val text = "A salon booking app in Delhi. Customers pay with UPI, about 1200 bookings a month, " +
            "call 98765 43210 for details. Launch by 2026-12-01. Budget 5,00,000."
        assertEquals(emptyList<Kind>(), kinds(text))
    }

    @Test fun apiKeysAndTokens() {
        assertEquals(listOf(Kind.API_KEY), kinds("aws key AKIAIOSFODNN7EXAMPLE ok"))
        assertEquals(listOf(Kind.API_KEY), kinds("token ghp_" + "a".repeat(36)))
        assertEquals(listOf(Kind.API_KEY), kinds("use sk-proj-abcdefghij1234567890uvwx"))
        assertEquals(listOf(Kind.API_KEY), kinds("stripe sk_live_" + "4eC39HqLyjWDarjtT1zdp7dc"))
        assertEquals(listOf(Kind.API_KEY), kinds("slack xoxb-1234567890-abcdef"))
        assertEquals(listOf(Kind.API_KEY), kinds("maps AIza" + "SyD-9tSrke72PouQMnMX-a7eZSW0jkFMBWY"))
        // Razorpay, same cases as the iOS tests.
        assertEquals(listOf(Kind.API_KEY), kinds("My Razorpay key is rzp_live_" + "9aB3xQ7mK2pL5vN8"))
        assertEquals(listOf(Kind.API_KEY), kinds("Test with rzp_test_" + "1DP5mmOlF5G5ag."))
        assertEquals(emptyList<Kind>(), kinds("Payments through Razorpay, rzp_live_ keys come later"))
    }

    @Test fun privateKeyBlock() {
        val text = "here:\n-----BEGIN RSA PRIVATE KEY-----\nMIIEow\nabc\n-----END RSA PRIVATE KEY-----\nthanks"
        val f = SensitiveScan.scan(text).single()
        assertEquals(Kind.PRIVATE_KEY, f.kind)
        assertEquals("here:\n[removed]\nthanks", SensitiveScan.redact(text))
    }

    @Test fun passwordKeepsItsLabel() {
        val text = "Admin login is admin, password: Hunter2! and that's it"
        assertEquals(listOf(Kind.PASSWORD), kinds(text))
        assertEquals("Admin login is admin, password: [removed] and that's it", SensitiveScan.redact(text))
        assertEquals(emptyList<Kind>(), kinds("users reset their password by email"))
    }

    /** v0.9.4: a Claude key is removed whole, the "sk-ant-" prefix included. */
    @Test fun claudeKeyIsRemovedWhole() {
        val key = "sk-ant-api03-" + "Zx9_Qw-Er".repeat(10) + "AA"
        assertEquals("ANTHROPIC_API_KEY=[removed] in the env file", SensitiveScan.redact("ANTHROPIC_API_KEY=$key in the env file"))
        assertEquals(emptyList<Kind>(), kinds("we use the sk-ant prefix check and Anthropic's API"))
    }

    /** The server's L1 cases (Raviga Backend tests/test_screening.py): the phone flags the same. */
    @Test fun serverCasesAreFlaggedAsTheSameKind() {
        val cases = listOf(
            "key AKIAIOSFODNN7EXAMPLE here" to Kind.API_KEY,
            "temp ASIAIOSFODNN7EXAMPLE here" to Kind.API_KEY,
            "aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY" to Kind.API_KEY,
            "token ghp_" + "a".repeat(36) to Kind.API_KEY,
            "github_pat_" + "B".repeat(82) to Kind.API_KEY,
            "openai sk-proj-Ab12Cd34Ef56Gh78Ij90Kl12Mn34" to Kind.API_KEY,
            "claude sk-ant-api03-" + "Ab3_dE-f".repeat(12) to Kind.API_KEY,
            "claude sk-ant-usr-" + "AbCdEfGhIjKlMnOpQrStUvWx" to Kind.API_KEY,
            "legacy sk-" + "a1".repeat(24) to Kind.API_KEY,
            "stripe sk_live_" + "4eC39HqLyjWDarjtT1zdp7dc" to Kind.API_KEY,
            "restricted rk_live_" + "4eC39HqLyjWDarjtT1zdp7dc" to Kind.API_KEY,
            "razorpay rzp_live_" + "9aB3xQ7mK2pL5vN8 for payments" to Kind.API_KEY,
            "test rzp_test_" + "1DP5mmOlF5G5ag" to Kind.API_KEY,
            "Key Secret: thisisnotarealsecret1234" to Kind.PASSWORD,
            "client_secret=Ab3dEf9hIjKlMn" to Kind.PASSWORD,
            "secret_key = 9f8e7d6c5b4a" to Kind.PASSWORD,
            "slack xoxb-" + "1234567890-abcdefghij" to Kind.API_KEY,
            "maps AIza" + "SyA1234567890abcdefghijklmnopqrstuv" to Kind.API_KEY,
            "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----" to Kind.PRIVATE_KEY,
            "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQ" to Kind.PRIVATE_KEY,
            "password: Summer@2024" to Kind.PASSWORD,
            "pwd = hunter22" to Kind.PASSWORD,
            "api key: Zx9QwErTy" to Kind.PASSWORD,
            "UPI PIN = 4321" to Kind.PASSWORD,
            "ATM pin: 9876" to Kind.PASSWORD,
            "card 4111 1111 1111 1111 ok" to Kind.CARD,
            "card 5500-0000-0000-0004 ok" to Kind.CARD,
            "aadhaar 2341 2341 2346 ok" to Kind.AADHAAR,
            "aadhaar 234123412346 ok" to Kind.AADHAAR,
            "pan ABCPE1234F ok" to Kind.PAN,
        )
        for ((text, kind) in cases) {
            assertEquals(text, listOf(kind), kinds(text))
            assertTrue(text, SensitiveScan.redact(text).contains(SensitiveScan.REPLACEMENT))
        }
    }

    @Test fun serverFalsePositivesAreLeftAlone() {
        listOf(
            "call +91 98765 43210 or 9876543210 or 919876543210",
            "launch on 2026-11-15, budget 25,00,000",
            "order 2341 2341 2345 and card 4111 1111 1111 1112",
            "password: minimum 8 characters, forgot password flow",
            "PIN: 560001 is the office pincode",
            "we use scikit-learn and sk-learn style pipelines for the recommendation engine",
            "ABCDE12345 is a product code, ABCDE1234 too",
            "token: 5 per day",
            "password: [removed]",
        ).forEach { assertEquals(it, emptyList<Kind>(), kinds(it)) }
    }

    @Test fun cardNumbersNeedLuhn() {
        assertEquals(listOf(Kind.CARD), kinds("card 4111 1111 1111 1111 exp 12/29"))
        assertEquals(listOf(Kind.CARD), kinds("card 4111-1111-1111-1111"))
        assertEquals(emptyList<Kind>(), kinds("order 4111 1111 1111 1112"))
    }

    @Test fun aadhaarNeedsVerhoeffAndPanIsUppercase() {
        // 2341 2341 2346 passes Verhoeff; 2341 2341 2345 does not.
        assertTrue(SensitiveScan.verhoeff("234123412346"))
        assertFalse(SensitiveScan.verhoeff("234123412345"))
        assertEquals(listOf(Kind.AADHAAR), kinds("aadhaar 2341 2341 2346"))
        assertEquals(emptyList<Kind>(), kinds("ref 2341 2341 2345"))
        assertEquals(listOf(Kind.PAN), kinds("PAN ABCPE1234F please"))
        assertEquals(emptyList<Kind>(), kinds("abcpe1234f"))
    }

    @Test fun titleListsUpToTwoKinds() {
        val one = SensitiveScan.scan("4111 1111 1111 1111")
        assertEquals("This looks like it includes a card number.", SensitiveScan.title(one))
        val two = SensitiveScan.scan("4111 1111 1111 1111 and AKIAIOSFODNN7EXAMPLE")
        assertEquals("This looks like it includes a card number and an API key or token.", SensitiveScan.title(two))
        val three = SensitiveScan.scan("4111 1111 1111 1111, AKIAIOSFODNN7EXAMPLE, PAN ABCPE1234F")
        assertEquals("This looks like it includes a card number, an API key or token and other secrets.", SensitiveScan.title(three))
    }

    @Test fun redactReplacesEveryFinding() {
        val text = "key AKIAIOSFODNN7EXAMPLE card 4111111111111111 pan ABCPE1234F"
        assertEquals("key [removed] card [removed] pan [removed]", SensitiveScan.redact(text))
    }
}
