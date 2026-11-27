package ru.homebudget.finkeeper.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class EntitlementsOfflineGraceTest {
    private val activeProEntitlements = Entitlements(
        plan = "pro",
        status = "trial",
        isPro = true,
        isTrial = true,
        trialUsed = true,
        canStartTrial = false,
        startedAt = "2026-11-01T12:00:00Z",
        expiresAt = "2026-11-15T12:00:00Z",
        daysRemaining = 14,
        source = "trial",
        features = EntitlementFeatures(
            exportReports = true,
            extendedAnalytics = true,
            extendedBackups = true,
            unlimitedDevices = true,
            familyAccess = true,
            prioritySupport = true,
        ),
        limits = EntitlementLimits(
            maxBackups = 30,
            maxDevices = 99,
            analyticsMonths = 36,
            maxFamilyMembers = 5,
        ),
    )

    @Test
    fun `default free entitlements have all pro features disabled and canStartTrial enabled`() {
        val free = Entitlements.FREE_DEFAULT
        assertFalse(free.isPro)
        assertTrue(free.canStartTrial)
        assertFalse(free.features.exportReports)
        assertFalse(free.features.extendedAnalytics)
        assertFalse(free.features.extendedBackups)
        assertFalse(free.features.unlimitedDevices)
        assertEquals(5, free.limits.maxBackups)
        assertEquals(2, free.limits.maxDevices)
        assertEquals(6, free.limits.analyticsMonths)
    }

    @Test
    fun `before expiresAt entitlements remain active regardless of online or offline state`() {
        val nowMillis = Instant.parse("2026-11-10T12:00:00Z").toEpochMilliseconds()
        val online = activeProEntitlements.withOfflineGrace(isOffline = false, nowEpochMillis = nowMillis)
        val offline = activeProEntitlements.withOfflineGrace(isOffline = true, nowEpochMillis = nowMillis)

        assertTrue(online.isPro)
        assertEquals("trial", online.status)
        assertTrue(offline.isPro)
        assertEquals("trial", offline.status)
    }

    @Test
    fun `offline within 7-day grace period preserves PRO features`() {
        // 3 дня после expiresAt (2026-11-18 < 2026-11-15 + 7 дней)
        val threeDaysAfterExpiry = Instant.parse("2026-11-18T12:00:00Z").toEpochMilliseconds()
        val graced = activeProEntitlements.withOfflineGrace(
            isOffline = true,
            nowEpochMillis = threeDaysAfterExpiry,
        )

        assertTrue(graced.isPro)
        assertEquals("grace_period", graced.status)
        assertTrue(graced.features.exportReports)
        assertTrue(graced.features.extendedAnalytics)
        assertTrue(graced.features.extendedBackups)
        assertEquals(30, graced.limits.maxBackups)
    }

    @Test
    fun `offline after 7-day grace period expires downgrades to free`() {
        // 8 дней после expiresAt (2026-11-23 > 2026-11-15 + 7 дней)
        val eightDaysAfterExpiry = Instant.parse("2026-11-23T12:00:00Z").toEpochMilliseconds()
        val expired = activeProEntitlements.withOfflineGrace(
            isOffline = true,
            nowEpochMillis = eightDaysAfterExpiry,
        )

        assertFalse(expired.isPro)
        assertEquals("free", expired.plan)
        assertEquals("expired", expired.status)
        assertTrue(expired.trialUsed)
        assertFalse(expired.canStartTrial)
        assertFalse(expired.features.exportReports)
        assertEquals(5, expired.limits.maxBackups)
    }

    @Test
    fun `online after expiresAt downgrades immediately without offline grace`() {
        val oneDayAfterExpiry = Instant.parse("2026-11-16T12:00:00Z").toEpochMilliseconds()
        val onlineExpired = activeProEntitlements.withOfflineGrace(
            isOffline = false,
            nowEpochMillis = oneDayAfterExpiry,
        )

        assertFalse(onlineExpired.isPro)
        assertEquals("free", onlineExpired.plan)
        assertEquals("expired", onlineExpired.status)
    }
}
