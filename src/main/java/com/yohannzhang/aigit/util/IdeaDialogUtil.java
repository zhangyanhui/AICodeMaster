package com.yohannzhang.aigit.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

/**
 * IdeaDialogUtil
 *
 * @author hmydk
 */
public class IdeaDialogUtil {

    public static void showWarning(Project project, String message, String title) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project != null && !project.isDisposed()) {
                Messages.showWarningDialog(project, message, title);
            } else {
                Messages.showWarningDialog(message, title);
            }
        });
    }

    public static void showError(Project project, String message, String title) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project != null && !project.isDisposed()) {
                Messages.showErrorDialog(project, message, title);
            } else {
                Messages.showErrorDialog(message, title);
            }
        });
    }

    public static void handleModuleNecessaryConfigIsWrong(Project project) {
        showWarning(project, "Please check the necessary configuration.",
                "Necessary Configuration Error");
    }

    public static void handleNoChangesSelected(Project project) {
        showWarning(project, "No changes selected. Please select files to commit.",
                "No Changes Selected");
    }

    public static void handleGenerationError(Project project, String errorMessage) {
        showError(project, "Error generating commit message: " + errorMessage, "Error");
    }

}
