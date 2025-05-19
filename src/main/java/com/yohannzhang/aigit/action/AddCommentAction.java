package com.yohannzhang.aigit.action;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class AddCommentAction extends AnAction {
    private static final CodeUtil CODE_UTIL = new CodeUtil();
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";
    private final StringBuilder messageBuilder = new StringBuilder();

    @Override
    public void actionPerformed(AnActionEvent event) {
//        if (!ActionControl.startAction()) {
//            Messages.showMessageDialog("正在处理任务，请稍后再试.", "Warning", Messages.getWarningIcon());
//            return;
//        }
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            showWarningDialog("No active editor found!");
            return;
        }

        final String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            showWarningDialog("No code selected!");
            return;
        }

        Project project = event.getProject();
        if (project == null) {
            return;
        }


        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow == null) {
            return;
        }

        toolWindow.show(() -> {});
        processSelectedCode(project, selectedText);
    }

    private void processSelectedCode(Project project, String selectedText) {
        CodeService codeService = new CodeService();
        String formattedCode = CODE_UTIL.formatCode(selectedText);
        String prompt = buildPrompt(formattedCode);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, Constants.TASK_TITLE, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    if (codeService.generateByStream()) {
                        generateCommentMessage(project, codeService, prompt);
                    }
                } catch (IllegalArgumentException ex) {
                    showConfigWarning(project, ex);
                } catch (Exception ex) {
                    showError(project, "Error generating commit message: " + ex.getMessage());
                }
            }
        });
    }

    private String buildPrompt(String code) {
        return "你是一个Java代码注释专家，请根据给定的代码片段，添加注释。要求用中文注释解释关键代码，" +
                "打印日志的代码不生成注释。方法注释生成在方法上方，代码注释生成在对应代码上方。" +
                "最后分析复杂度。代码如下：" + code;
    }

    private void generateCommentMessage(Project project, CodeService codeService, String prompt) throws Exception {
        messageBuilder.setLength(0);
//        CombinedWindowFactory combinedWindowFactory = project.getComponent(CombinedWindowFactory.class);
        codeService.generateCommitMessageStream(
                prompt,
                token -> ApplicationManager.getApplication().invokeLater(() -> {
                    messageBuilder.append(token);
                    CombinedWindowFactory.updateResult(messageBuilder.toString());
                }),
                error -> ApplicationManager.getApplication().invokeLater(() ->
                        showError(project, "Error generating commit message: " + error.getMessage()))
        );
    }

    private void showWarningDialog(String message) {
        Messages.showMessageDialog(message, "Warning", Messages.getWarningIcon());
    }

    private void showError(Project project, String message) {
        IdeaDialogUtil.showError(project, message, "Error");
    }

    private void showConfigWarning(Project project, IllegalArgumentException ex) {
        IdeaDialogUtil.showWarning(project, ex.getMessage() + "\n ----Please check your module config.",
                "AI Commit Message Warning");
    }
}