package com.yohannzhang.aigit.utils;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

public class NotificationUtils {
    private static final String NOTIFICATION_GROUP_ID = "AIGit Notifications";

    public static void showInfo(String title, String content) {
        showNotification(title, content, NotificationType.INFORMATION);
    }

    public static void showError(String content) {
        showNotification("Error", content, NotificationType.ERROR);
    }

    public static void showWarning(String title, String content) {
        showNotification(title, content, NotificationType.WARNING);
    }

    private static void showNotification(String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, type)
            .notify(null);
    }
} 