package `in`.financeministry.app

internal data class PermissionSetupNeeds(val sms: Boolean, val notifications: Boolean)

internal fun permissionSetupNeeds(smsGranted: Boolean, smsConsent: Boolean, smsPreviouslyRequested: Boolean,
    recordingNotificationsEnabled: Boolean, recordingNotificationsAvailable: Boolean): PermissionSetupNeeds {
    // Existing Pause SMS capture clears consent but keeps request history. Do not
    // mistake that deliberate choice for an uncompleted first-time setup.
    val intentionallyPaused = smsPreviouslyRequested && !smsConsent
    return PermissionSetupNeeds(!intentionallyPaused && (!smsGranted || !smsConsent),
        recordingNotificationsEnabled && !recordingNotificationsAvailable)
}

internal fun permissionSetupSnoozed(dismissedAt: Long, dismissedVersion: Int, currentVersion: Int, now: Long): Boolean =
    dismissedAt > 0 && dismissedVersion == currentVersion &&
        (now < dismissedAt || now - dismissedAt < 7L * 24 * 60 * 60 * 1000)

internal enum class NotificationSetupAction { None, RequestPermission, OpenSettings }

internal fun notificationSetupAction(available: Boolean, runtimePermissionRequired: Boolean,
    runtimePermissionGranted: Boolean, previouslyRequested: Boolean, canShowRationale: Boolean): NotificationSetupAction = when {
    available -> NotificationSetupAction.None
    runtimePermissionRequired && !runtimePermissionGranted && (!previouslyRequested || canShowRationale) -> NotificationSetupAction.RequestPermission
    else -> NotificationSetupAction.OpenSettings
}
