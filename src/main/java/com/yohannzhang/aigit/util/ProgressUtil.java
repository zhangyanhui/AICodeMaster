package com.yohannzhang.aigit.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ProgressUtil {

    public static void runWithProgress(@NotNull Project project,
                                       @NotNull String title,
                                       @NotNull String message,
                                       boolean canBeCancelled,
                                       @NotNull Runnable task) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title, canBeCancelled) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText(message);
                indicator.setIndeterminate(true);
                try {
                    task.run();
                } catch (Exception e) {
                    indicator.setText("Error: " + e.getMessage());
                    throw e;
                }
            }
        });
    }

    public static void runWithProgressAndPercentage(@NotNull Project project,
                                                    @NotNull String title,
                                                    @NotNull String message,
                                                    boolean canBeCancelled,
                                                    @NotNull ProgressTask task) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title, canBeCancelled) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText(message);
                indicator.setIndeterminate(false);
                indicator.setFraction(0.0);
                try {
                    task.run(indicator);
                } catch (Exception e) {
                    indicator.setText("Error: " + e.getMessage());
                    try {
                        throw e;
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });
    }

    @FunctionalInterface
    public interface ProgressTask {
        void run(ProgressIndicator indicator) throws Exception;
    }
} 