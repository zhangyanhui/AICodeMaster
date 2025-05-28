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
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.diff.impl.patch.formove.PatchApplier.showError;

public class ShowMeYourCodeAction extends AnAction {

    private static final CodeUtil codeUtil = new CodeUtil();
    private final StringBuilder messageBuilder = new StringBuilder();

    @Override
    public void actionPerformed(AnActionEvent event) {
//        if (!ActionControl.startAction()) {
//            Messages.showMessageDialog("正在处理任务，请稍后再试.", "Warning", Messages.getWarningIcon());
//            return;
//        }
        // 获取编辑器
        Editor editor = event.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);

        if (editor == null) {
            Messages.showMessageDialog("No active editor found!", "Warning", Messages.getWarningIcon());
            return;
        }

        // 获取选中的文本
        String selectedText = editor.getSelectionModel().getSelectedText();

        // 如果没有选中的文本，显示警告
        if (selectedText == null || selectedText.isEmpty()) {
            Messages.showMessageDialog("你想问什么!", "Warning", Messages.getWarningIcon());
        } else {
            Project project = event.getProject();
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AICodeMaster");
            if (toolWindow != null) {
                toolWindow.show(() -> {
                });
                // 根据配置，创建对应的服务
                CodeService codeService = new CodeService();
                String code = ShowMeYourCodeAction.codeUtil.formatCode(selectedText);
                String prompt = "你是一个Java代码开发专家，请根据给定的文字描述，用中文生成相应的代码及注释，格式分三部分：1.文字描述 2.代码及注释，对应注释在代码上方 3.总结。文字如下：" + code;
                // Run the time-consuming operations in a background task
                ProgressManager.getInstance().run(new Task.Backgroundable(project, Constants.TASK_TITLE, true) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        try {
//                            CombinedWindowFactory combinedWindowFactory = project.getComponent(CombinedWindowFactory.class);

                            //  String diff = GItCommitUtil.computeDiff(includedChanges, includedUnversionedFiles, project);
//                    System.out.println("diff: " + diff);
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
                            } else {
//                                    String commitMessageFromAi = commitMessageService.generateCommitMessage(diff).trim();
//                                    ApplicationManager.getApplication().invokeLater(() -> {
//                                        commitMessage.setCommitMessage(branch.getName()+":" +commitMessageFromAi);
//                                    });
                            }
                        } catch (IllegalArgumentException ex) {
                            IdeaDialogUtil.showWarning(project, ex.getMessage() + "\n ----Please check your module config.",
                                    "AI Commit Message Warning");
                        } catch (Exception ex) {
                            IdeaDialogUtil.showError(project, "Error generating commit message: " + ex.getMessage(), "Error");
                        }
                    }

                    private void handleTokenResponse(String token) {
                        ApplicationManager.getApplication().invokeLater(() -> {
//                            messageBuilder.append(token);
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
        }
    }
}
