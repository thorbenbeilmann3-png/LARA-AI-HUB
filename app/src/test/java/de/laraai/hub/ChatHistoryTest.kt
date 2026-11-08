package de.laraai.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryTest {
    private fun u(text: String) = ChatTurn("user", text)
    private fun a(text: String) = ChatTurn("assistant", text)

    @Test
    fun emptyHistorySendsOnlyPrompt() {
        assertEquals(listOf(u("Hallo")), ChatHistory.build(emptyList(), "Hallo"))
    }

    @Test
    fun previousTurnsAreSentInOrderBeforePrompt() {
        val result = ChatHistory.build(listOf(u("Ich heiße Thorben."), a("Hallo Thorben!")), "Wie heiße ich?")
        assertEquals(listOf(u("Ich heiße Thorben."), a("Hallo Thorben!"), u("Wie heiße ich?")), result)
    }

    @Test
    fun consecutiveUserTurnsAreMerged() {
        // e.g. an earlier question whose answer failed
        val result = ChatHistory.build(listOf(u("Frage 1")), "Frage 2")
        assertEquals(listOf(u("Frage 1\n\nFrage 2")), result)
    }

    @Test
    fun leadingAssistantTurnIsDropped() {
        val result = ChatHistory.build(listOf(a("alt"), u("q"), a("r")), "neu")
        assertEquals("user", result.first().role)
        assertEquals(listOf(u("q"), a("r"), u("neu")), result)
    }

    @Test
    fun rolesAlternateAndEndWithUser() {
        val history = listOf(u("1"), a("2"), a("3"), u("4"), u("5"), a("6"))
        val result = ChatHistory.build(history, "7")
        for (i in 1 until result.size) assertFalse(result[i].role == result[i - 1].role)
        assertEquals(u("7"), result.last())
    }

    @Test
    fun turnLimitKeepsMostRecent() {
        val history = (1..30).flatMap { listOf(u("q$it"), a("a$it")) }
        val result = ChatHistory.build(history, "neu", maxTurns = 5)
        assertTrue(result.size <= 5)
        assertEquals(u("neu"), result.last())
        assertEquals(a("a30"), result[result.size - 2])
    }

    @Test
    fun charBudgetKeepsMostRecentAndAlwaysThePrompt() {
        val history = listOf(u("x".repeat(5000)), a("y".repeat(5000)), u("kurz"), a("auch kurz"))
        val result = ChatHistory.build(history, "neu", maxChars = 100)
        assertEquals(listOf(u("kurz"), a("auch kurz"), u("neu")), result)
        assertEquals(listOf(u("z".repeat(500))), ChatHistory.build(history, "z".repeat(500), maxChars = 100))
    }

    @Test
    fun blankAndUnknownRolesAreIgnored() {
        val result = ChatHistory.build(listOf(ChatTurn("system", "s"), u("  "), u("q"), a("r")), "neu")
        assertEquals(listOf(u("q"), a("r"), u("neu")), result)
    }

    @Test
    fun storedRolesMapToModelRoles() {
        assertEquals("user", ChatHistory.fromStoredRole("Du"))
        assertEquals("assistant", ChatHistory.fromStoredRole("LARA / qwen2.5-0.5b-instruct-q4_k_m"))
        assertEquals(null, ChatHistory.fromStoredRole("System"))
    }
}

class CleartextPolicyTest {
    @Test
    fun loopbackHttpAllowed() {
        assertTrue(CleartextPolicy.isAllowed("http://127.0.0.1:12434/"))
        assertTrue(CleartextPolicy.isAllowed("http://localhost:8765"))
    }

    @Test
    fun httpsAlwaysAllowed() {
        assertTrue(CleartextPolicy.isAllowed("https://api.anthropic.com"))
    }

    @Test
    fun otherHttpRejected() {
        for (url in listOf(
            "http://10.179.130.230:12434/",
            "http://192.168.1.20:18789/v1",
            "http://127.0.0.1.evil.example/",
            "http://localhost.evil.example/",
            "http://[::1]:12434/",
            "http://"
        )) {
            assertFalse(url, CleartextPolicy.isAllowed(url))
        }
    }
}
