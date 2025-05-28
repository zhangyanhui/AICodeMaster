package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import com.yohannzhang.aigit.util.ActionControl;
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 代码优化处理类
 *
 * @author yohannzhang
 * @date 2025/5/16 13:08
 * @since 1.0.0
 */

public class OptimizeCodeAction extends AnAction {
    private static final CodeUtil CODE_UTIL = new CodeUtil();
    private final StringBuilder messageBuilder = new StringBuilder();
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";

    @Override
    public void actionPerformed(AnActionEvent event) {
//        if (!ActionControl.startAction()) {
//            Messages.showMessageDialog("正在处理任务，请稍后再试.", "Warning", Messages.getWarningIcon());
//            return;
//        }

        Editor editor = event.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        if (editor == null) {
            ActionControl.endAction();
            showWarningDialog("No active editor found!");
            return;
        }

//        String selectedText = editor.getSelectionModel().getSelectedText();
        //选中当前文件
        String selectedText = editor.getDocument().getText();
        if (selectedText == null || selectedText.isEmpty()) {
            ActionControl.endAction();
            showWarningDialog("你想问什么!");
            return;
        }

        Project project = event.getProject();
        if (project == null) {
            ActionControl.endAction();
            showWarningDialog("No active project found!");
            return;
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow == null) {
            ActionControl.endAction();
            showWarningDialog("Result window not available!");
            return;
        }

        toolWindow.show(null);
        try {
            processCodeOptimization(project, selectedText);
        } finally {
            ActionControl.endAction();
        }
    }

    private void processCodeOptimization(Project project, String selectedText) {
        CodeService codeService = new CodeService();
        String formattedCode = CODE_UTIL.formatCode(selectedText);
        String prompt = String.format(
                "你是一个Java代码开发专家，请根据给出的代码，用中文提出相应的优化建议。" +
                        "生成回答输出格式分为四部分，分别为1：代码的解释，" +
                        "2：优化后的代码，3：优化点解释。待优化代码如下：%s",
                formattedCode
        );

        ProgressManager.getInstance().run(new Task.Backgroundable(project, Constants.TASK_TITLE, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    if (codeService.generateByStream()) {
                        messageBuilder.setLength(0);
                        codeService.generateCommitMessageStream(
                                prompt,
                                token -> {
                                    messageBuilder.append(token);
                                    String fullMarkdown = messageBuilder.toString();
                                    handleTokenResponse(fullMarkdown);
                                },
                                this::handleErrorResponse,
                                () -> ApplicationManager.getApplication().invokeLater(() -> {
                                    CombinedWindowFactory.getInstance(project).resetButton(project);

                                })
                        );
                    }
                } catch (IllegalArgumentException ex) {
                    showConfigWarning(project, ex);
                } catch (Exception ex) {
                    showError(project, "Error generating commit message: " + ex.getMessage());
                }
            }

            private void handleTokenResponse(String token) {
                ApplicationManager.getApplication().invokeLater(() -> {
//                    messageBuilder.append(token);
                    CombinedWindowFactory.getInstance(project).updateResult(messageBuilder.toString(), project);
                    CombinedWindowFactory.getInstance(project).submitButton(project);

                });
            }

            private void handleErrorResponse(Throwable error) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    showError(project, "Error generating commit message: " + error.getMessage());
                });
            }
        });
    }

    private void showWarningDialog(String message) {
        Messages.showMessageDialog(message, "Warning", Messages.getWarningIcon());
    }

    private void showError(Project project, String message) {
        IdeaDialogUtil.showError(project, message, "Error");
    }

    private void showConfigWarning(Project project, IllegalArgumentException ex) {
        IdeaDialogUtil.showWarning(
                project,
                ex.getMessage() + "\n ----Please check your module config.",
                "AI Commit Message Warning"
        );
    }
}