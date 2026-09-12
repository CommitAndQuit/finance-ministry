package `in`.financeministry.app

import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.sms.ReviewReminder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewReminderIntegrationTest {
    @Test fun schedule_and_cancel_update_alarm_and_preference_together() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("finance_settings", android.content.Context.MODE_PRIVATE)
        ReviewReminder.cancel(context)
        try {
            ReviewReminder.schedule(context, 20, 15)
            assertTrue(ReviewReminder.isScheduled(context))
            assertTrue(preferences.getBoolean("review_reminder", false))
        } finally { ReviewReminder.cancel(context) }
        assertFalse(ReviewReminder.isScheduled(context))
        assertFalse(preferences.getBoolean("review_reminder", true))
    }

    @Test fun cancellation_invalidates_an_inflight_receiver_and_prevents_reschedule() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ReviewReminder.cancel(context)
        ReviewReminder.schedule(context, 20, 16)
        val staleGeneration = ReviewReminder.generation(context)
        ReviewReminder.cancel(context)
        assertFalse(ReviewReminder.deliverIfCurrent(context, staleGeneration, hasReview = true))
        assertFalse(ReviewReminder.scheduleNextIfCurrent(context, staleGeneration))
        assertFalse(ReviewReminder.isScheduled(context))
    }

    @Test fun reminder_time_is_remembered_while_the_reminder_is_off() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("finance_settings", android.content.Context.MODE_PRIVATE)
        ReviewReminder.cancel(context)
        ReviewReminder.updateTime(context, 7, 35)
        assertEquals(7, preferences.getInt("review_reminder_hour", -1))
        assertEquals(35, preferences.getInt("review_reminder_minute", -1))
        assertFalse(preferences.getBoolean("review_reminder", true))
        assertFalse(ReviewReminder.isScheduled(context))
    }

    @Test fun erase_then_reenable_cannot_revalidate_a_pre_erase_callback() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("finance_settings", android.content.Context.MODE_PRIVATE)
        ReviewReminder.cancel(context)
        ReviewReminder.schedule(context, 20, 17)
        val beforeErase = ReviewReminder.generation(context)
        preferences.edit().clear().commit()
        ReviewReminder.cancel(context)
        ReviewReminder.schedule(context, 20, 18)
        assertFalse(beforeErase == ReviewReminder.generation(context))
        assertFalse(ReviewReminder.deliverIfCurrent(context, beforeErase, hasReview = true))
        ReviewReminder.cancel(context)
    }
}
