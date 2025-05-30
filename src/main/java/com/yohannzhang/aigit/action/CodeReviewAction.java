package com.yohannzhang.aigit.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.util.GItCommitUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CodeReviewAction extends AnAction {
    private static final String DEFAULT_TEXT = "AI Code Review";
    private static final String PROCESSING_TEXT = "Reviewing...";
    private volatile boolean isProcessing = false;

    public CodeReviewAction() {
        super(DEFAULT_TEXT, "Perform AI code review on changes", AllIcons.Actions.Show);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(project != null && !isProcessing);
        presentation.setText(isProcessing ? PROCESSING_TEXT : DEFAULT_TEXT);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        if (isProcessing) {
            return;
        }

        isProcessing = true;
        update(e);

        try {
            AbstractCommitWorkflowHandler<?, ?> commitWorkflowHandler = (AbstractCommitWorkflowHandler<?, ?>) e.getData(
                    VcsDataKeys.COMMIT_WORKFLOW_HANDLER);
            if (commitWorkflowHandler == null) {
                IdeaDialogUtil.handleNoChangesSelected(project);
                resetState(e);
                return;
            }

            List<Change> includedChanges = commitWorkflowHandler.getUi().getIncludedChanges();
            if (includedChanges.isEmpty()) {
                IdeaDialogUtil.handleNoChangesSelected(project);
                resetState(e);
                return;
            }

            String diff = GItCommitUtil.computeDiff(includedChanges, null, project);
            if (diff.isEmpty()) {
                IdeaDialogUtil.showWarning(project, "No changes to review.", "Code Review");
                resetState(e);
                return;
            }

            CodeService codeService = new CodeService();
            String prompt = String.format("请对以下代码改动进行代码审查，重点关注：\n" +
                    "1. 代码质量和最佳实践\n" +
                    "2. 潜在的问题和风险\n" +
                    "3. 性能优化建议\n" +
                    "4. 安全性考虑\n" +
                    "5. 可维护性建议\n\n" +
                    "代码改动如下：\n%s", diff);

            codeService.generateCommitMessageStream(
                    prompt,
                    token -> {
                        CommitMessage commitMessage = (CommitMessage) e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL);
                        if (commitMessage != null) {
                            commitMessage.setCommitMessage(commitMessage.getComment() + token);
                        }
                    },
                    error -> {
                        IdeaDialogUtil.showError(project, "Code review failed: " + error.getMessage(), "Error");
                        resetState(e);
                    },
                    () -> resetState(e)
            );

        } catch (Exception ex) {
            IdeaDialogUtil.showError(project, "Error during code review: " + ex.getMessage(), "Error");
            resetState(e);
        }
    }

    private void resetState(AnActionEvent e) {
        isProcessing = false;
        update(e);
    }
} 