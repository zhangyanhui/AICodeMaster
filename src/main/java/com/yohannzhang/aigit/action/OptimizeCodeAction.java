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
 * 代码Review处理类
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
            showWarningDialog("请选中代码后再进行Code Review!");
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
            processCodeReview(project, selectedText);
        } finally {
            ActionControl.endAction();
        }
    }

    private void processCodeReview(Project project, String selectedText) {
        CodeService codeService = new CodeService();
        String formattedCode = CODE_UTIL.formatCode(selectedText);
        String prompt = String.format(
                "你是一个Java代码Review专家，请对给出的代码进行全面的Code Review。" +
                        "重点检查空指针、内存溢出、线程安全、异常处理、性能问题等方面。" +
                        "\n\n**重要要求：对于发现的每个问题，必须同时提供：**\n" +
                        "1. 具体的代码行号定位\n" +
                        "2. 完整的源代码片段（包含上下文）\n" +
                        "3. 详细的问题描述和修复建议\n" +
                        "\n输出格式要求：\n" +
                        "❌ **[问题类型]** (第X-Y行): 问题详细描述\n" +
                        "\n" +
                        "**问题代码：**\n" +
                        "```java\n" +
                        "// 第X行开始\n" +
                        "完整的问题代码片段（包含足够的上下文）\n" +
                        "// 第Y行结束\n" +
                        "```\n" +
                        "\n" +
                        "**问题分析：** 详细说明为什么这段代码有问题\n" +
                        "\n" +
                        "✅ **修复建议：**\n" +
                        "```java\n" +
                        "// 修复后的代码\n" +
                        "完整的修复后代码片段\n" +
                        "```\n" +
                        "\n" +
                        "---\n" +
                        "\n" +
                        "如果代码质量良好，请用以下格式说明：\n" +
                        "✅ **代码质量评估：** 经过详细review，该代码段在以下方面表现良好：[具体说明]\n" +
                        "\n" +
                        "待Review代码如下：%s",
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
                    showError(project, "Error performing code review: " + ex.getMessage());
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
                    showError(project, "Error performing code review: " + error.getMessage());
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