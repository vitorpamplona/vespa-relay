/*
 * Copyright (c) 2026 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.eventstore.relay

import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class RelayConfigTest {
    @Test
    fun `limits default when env is empty`() {
        val d = relayLimitsFromEnv(emptyMap())
        assertEquals(20, d.maxFilters)
        assertEquals(50, d.maxSubscriptions)
        assertEquals(5_000, d.maxLimit)
        assertEquals(false, d.authRequired)
    }

    @Test
    fun `limits override only the fields set, ignoring garbage`() {
        val limits =
            relayLimitsFromEnv(
                mapOf(
                    "MAX_FILTERS" to "7",
                    "MAX_LIMIT" to "999",
                    "MAX_SUBSCRIPTIONS" to "not-a-number", // ignored -> default stands
                    "CREATED_AT_UPPER_LIMIT" to "1900000000",
                ),
            )
        assertEquals(7, limits.maxFilters)
        assertEquals(999, limits.maxLimit)
        assertEquals(50, limits.maxSubscriptions) // default kept
        assertEquals(1_900_000_000L, limits.createdAtUpperLimit)
    }

    @Test
    fun `negentropy defaults to the strfry-parity Default`() {
        assertEquals(NegentropySettings.Default, negentropySettingsFromEnv(emptyMap()))
    }

    @Test
    fun `negentropy overrides the caps that are set`() {
        val neg =
            negentropySettingsFromEnv(
                mapOf(
                    "NEG_FRAME_SIZE_LIMIT" to "120000",
                    "NEG_MAX_SYNC_EVENTS" to "42",
                ),
            )
        assertEquals(120_000L, neg.frameSizeLimit)
        assertEquals(42, neg.maxSyncEvents)
        assertEquals(NegentropySettings.Default.maxSessionsPerConnection, neg.maxSessionsPerConnection)
    }

    @Test
    fun `admin pubkeys are lowercased, deduped, and hex-validated`() {
        val a = "a".repeat(64)
        val keys =
            adminPubkeysFromEnv(
                mapOf(
                    "RELAY_ADMIN_PUBKEYS" to "${a.uppercase()}, $a, not-hex, ${"b".repeat(63)}, ${"c".repeat(64)}",
                ),
            )
        assertEquals(setOf(a, "c".repeat(64)), keys)
    }

    @Test
    fun `no admin pubkeys when unset`() {
        assertEquals(emptySet(), adminPubkeysFromEnv(emptyMap()))
    }

    @Test
    fun `write allow and deny lists parse pubkeys and kinds`() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        val env =
            mapOf(
                "ALLOW_PUBKEYS" to "${a.uppercase()} $b",
                "DENY_PUBKEYS" to "not-hex",
                "ALLOW_KINDS" to "0,1, 30023",
                "DENY_KINDS" to "4, junk",
            )
        assertEquals(setOf(a, b), allowPubkeysFromEnv(env))
        assertEquals(emptySet(), denyPubkeysFromEnv(env))
        assertEquals(setOf(0, 1, 30023), allowKindsFromEnv(env))
        assertEquals(setOf(4), denyKindsFromEnv(env))
    }

    @Test
    fun `reject-future defaults off and clamps negatives`() {
        assertEquals(0, rejectFutureSecondsFromEnv(emptyMap()))
        assertEquals(900, rejectFutureSecondsFromEnv(mapOf("REJECT_FUTURE_SECONDS" to "900")))
        assertEquals(0, rejectFutureSecondsFromEnv(mapOf("REJECT_FUTURE_SECONDS" to "-5")))
    }

    @Test
    fun `expiration sweep interval defaults to an hour`() {
        assertEquals(3_600L, expirationSweepSecondsFromEnv(emptyMap()))
        assertEquals(60L, expirationSweepSecondsFromEnv(mapOf("EXPIRATION_SWEEP_SECONDS" to "60")))
    }
}
