package `in`.financeministry.app

import org.junit.Assert.*
import org.junit.Test

class PermissionSetupTest {
    private fun needs(granted: Boolean = false, consent: Boolean = false, requested: Boolean = false,
        alertsEnabled: Boolean = true, alertsAvailable: Boolean = false) =
        permissionSetupNeeds(granted, consent, requested, alertsEnabled, alertsAvailable)

    @Test fun never_requested_permissions_offer_both_actions() {
        val result = needs()
        assertTrue(result.sms)
        assertTrue(result.notifications)
    }
    @Test fun granted_sms_still_needs_explicit_disclosure_consent() {
        assertTrue(needs(granted = true).sms)
        assertFalse(needs(granted = true, consent = true, requested = true).sms)
    }
    @Test fun denial_or_revocation_still_offers_sms_recovery() {
        assertTrue(needs(consent = true, requested = true).sms)
    }
    @Test fun intentional_capture_pause_is_not_treated_as_missing_setup() {
        assertFalse(needs(granted = true, requested = true).sms)
        assertFalse(needs(granted = false, requested = true).sms)
    }
    @Test fun intentionally_disabled_recording_alerts_are_not_nudged() {
        assertFalse(needs(alertsEnabled = false).notifications)
    }
    @Test fun completed_permissions_remove_all_setup_actions() {
        val result = needs(granted = true, consent = true, requested = true, alertsAvailable = true)
        assertFalse(result.sms)
        assertFalse(result.notifications)
    }
    @Test fun not_now_snoozes_for_seven_days_with_an_exact_expiry() {
        assertTrue(permissionSetupSnoozed(1000L, 23, 23, 604800999L))
        assertFalse(permissionSetupSnoozed(1000L, 23, 23, 604801000L))
    }
    @Test fun new_version_reoffers_setup_even_during_a_snooze() {
        assertFalse(permissionSetupSnoozed(1000L, 23, 24, 2000L))
        assertFalse(permissionSetupSnoozed(0L, 0, 23, 2000L))
    }
    @Test fun clock_rollback_does_not_immediately_repeat_a_dismissed_nudge() {
        assertTrue(permissionSetupSnoozed(2000L, 23, 23, 1000L))
    }
    @Test fun available_notifications_never_open_a_permission_dialog() {
        assertEquals(NotificationSetupAction.None, notificationSetupAction(true, true, true, true, false))
    }
    @Test fun first_request_and_retry_with_rationale_use_the_runtime_prompt() {
        assertEquals(NotificationSetupAction.RequestPermission, notificationSetupAction(false, true, false, false, false))
        assertEquals(NotificationSetupAction.RequestPermission, notificationSetupAction(false, true, false, true, true))
    }
    @Test fun repeated_denial_and_blocked_app_or_channel_use_settings() {
        assertEquals(NotificationSetupAction.OpenSettings, notificationSetupAction(false, true, false, true, false))
        assertEquals(NotificationSetupAction.OpenSettings, notificationSetupAction(false, true, true, true, false))
        assertEquals(NotificationSetupAction.OpenSettings, notificationSetupAction(false, false, true, false, false))
    }
}
