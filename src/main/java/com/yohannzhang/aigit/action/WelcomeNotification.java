package com.yohannzhang.aigit.action;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity; // 更改为 StartupActivity
import org.jetbrains.annotations.NotNull;

public class WelcomeNotification implements StartupActivity { // 更改为 StartupActivity

    private static final String NOTIFICATION_GROUP_ID = "AI Git Commit Notifications";
    private static final String PLUGIN_NAME = "AI Git Commit";
    private static final String WELCOME_TITLE = "Welcome to AI Git Commit!";
    private static final String WELCOME_CONTENT = "Thank you for installing AI Git Commit. " +
            "To get started, please configure the plugin in the settings.";
    private static final String PLUGIN_VERSION_PROPERTY = "com.hmydk.aigit.version";

    @Override
    public void runActivity(@NotNull Project project) {
        if (isNewOrUpdatedVersion()) {
            showWelcomeNotification(project);
            updateStoredVersion();
        }
    }

    private boolean isNewOrUpdatedVersion() {
        String storedVersion = PropertiesComponent.getInstance().getValue(PLUGIN_VERSION_PROPERTY);
        String currentVersion = getCurrentPluginVersion();
        return storedVersion == null || !storedVersion.equals(currentVersion);
    }

    private void updateStoredVersion() {
        PropertiesComponent.getInstance().setValue(PLUGIN_VERSION_PROPERTY, getCurrentPluginVersion());
    }

    private String getCurrentPluginVersion() {
        return PluginManagerCore.getPlugin(PluginId.getId("com.yohannzhang.aigit")).getVersion();
    }

    private void showWelcomeNotification(@NotNull Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(WELCOME_TITLE, WELCOME_CONTENT, NotificationType.INFORMATION)
                .setIcon(null) // You can set a custom icon here if you have one
                .addAction(new ConfigureAction())
                .notify(project);
    }

    private static class ConfigureAction extends com.intellij.openapi.actionSystem.AnAction {
        ConfigureAction() {
            super("Configure");
        }

        @Override
        public void actionPerformed(@NotNull com.intellij.openapi.actionSystem.AnActionEvent e) {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), PLUGIN_NAME);
        }
    }
}
