package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/** Authorizes MediaSessionManager only. Receives no notification types and never restores Home. */
public class MediaAccess extends NotificationListenerService {}
