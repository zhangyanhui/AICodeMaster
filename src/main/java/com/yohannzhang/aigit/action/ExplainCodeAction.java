package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.ProgressUtil;
import org.jetbrains.annotations.NotNull;

public class ExplainCodeAction extends AnAction {
    private static final CodeUtil CODE_UTIL = new CodeUtil();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE);
        if (editor == null || psiFile == null) return;

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            com.intellij.openapi.ui.Messages.showWarningDialog(
                project,
                "Please select the code you want to explain.",
                "No Code Selected"
            );
            return;
        }

        String formattedCode = CODE_UTIL.formatCode(selectedText);
        String prompt = String.format("解释以下代码的功能和实现原理：\n%s", formattedCode);

        ProgressUtil.runWithProgress(
            project,
            "Explaining Code",
            "Analyzing and explaining code...",
            true,
            () -> {
                CodeService codeService = new CodeService();
                try {
                    if (codeService.generateByStream()) {
                        codeService.generateCommitMessageStream(
                            prompt,
                            token -> {
                                // 使用 WriteCommandAction 更新文档
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    WriteCommandAction.runWriteCommandAction(project, () -> {
                                        editor.getDocument().insertString(
                                            editor.getSelectionModel().getSelectionStart(),
                                            token
                                        );
                                    });
                                });
                            },
                            error -> {
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    com.intellij.openapi.ui.Messages.showErrorDialog(
                                        project,
                                        "Failed to explain code: " + error.getMessage(),
                                        "Error"
                                    );
                                });
                            },
                            () -> {
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    WriteCommandAction.runWriteCommandAction(project, () -> {
                                        editor.getSelectionModel().removeSelection();
                                    });
                                });
                            }
                        );
                    }
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        com.intellij.openapi.ui.Messages.showErrorDialog(
                            project,
                            "Failed to explain code: " + ex.getMessage(),
                            "Error"
                        );
                    });
                }
            }
        );
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(project != null && editor != null);
    }
} 