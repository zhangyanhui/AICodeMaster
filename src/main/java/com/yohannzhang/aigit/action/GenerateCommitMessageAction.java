package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.CommitMessageService;
import com.yohannzhang.aigit.util.GItCommitUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Action 类，用于生成 Git commit 消息
 * 继承自 AnAction 以集成到 IDEA 的操作系统中
 */
public class GenerateCommitMessageAction extends AnAction {

    /**
     * 获取CommitMessage对象
     */
    private CommitMessage getCommitMessage(AnActionEvent e) {
        return (CommitMessage) e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL);
    }

    private final StringBuilder messageBuilder = new StringBuilder();

    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // 点击后按钮设置为处理中
        ApplicationManager.getApplication().invokeLater(() -> {
            e.getPresentation().setEnabled(false);
//            ActionManager.getInstance().fireAnActionPropertyChanged(this, e.getPresentation());
        });
        try {
            CommitMessageService commitMessageService = new CommitMessageService();

            if (!commitMessageService.checkNecessaryModuleConfigIsRight()) {
                IdeaDialogUtil.handleModuleNecessaryConfigIsWrong(project);
                return;
            }

            AbstractCommitWorkflowHandler<?, ?> commitWorkflowHandler = (AbstractCommitWorkflowHandler<?, ?>) e.getData(
                    VcsDataKeys.COMMIT_WORKFLOW_HANDLER);
            if (commitWorkflowHandler == null) {
                IdeaDialogUtil.handleNoChangesSelected(project);
                return;
            }

            GitRepositoryManager gitRepositoryManager = GitRepositoryManager.getInstance(project);
            List<GitRepository> repositories = gitRepositoryManager.getRepositories();
            if (repositories.isEmpty()) {
                IdeaDialogUtil.showWarning(project, "No Git repository found.", "Git Repository Missing");
                return;
            }
            GitLocalBranch branch = repositories.get(0).getCurrentBranch();
            if (branch == null) {
                IdeaDialogUtil.showWarning(project, "Current branch is null.", "Git Branch Error");
                return;
            }

            CommitMessage commitMessage = getCommitMessage(e);

            List<Change> includedChanges = commitWorkflowHandler.getUi().getIncludedChanges();
            List<FilePath> includedUnversionedFiles = commitWorkflowHandler.getUi().getIncludedUnversionedFiles();

            if (includedChanges.isEmpty() && includedUnversionedFiles.isEmpty()) {
                commitMessage.setCommitMessage(Constants.NO_FILE_SELECTED);
                return;
            }

            String branchName = branch.getName();
            if (branchName == null) {
                branchName = "unknown";
            }

            commitMessage.setCommitMessage(branchName + ":" + Constants.GENERATING_COMMIT_MESSAGE);

            String diff = GItCommitUtil.computeDiff(includedChanges, includedUnversionedFiles, project);

            String finalBranchName = branchName;
            ProgressManager.getInstance().run(new Task.Backgroundable(project, Constants.TASK_TITLE, true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    StringBuilder messageBuilder = new StringBuilder(); // 避免线程冲突

                    try {
                        if (commitMessageService.generateByStream()) {
                            commitMessageService.generateCommitMessageStream(
                                    diff,
                                    token -> ApplicationManager.getApplication().invokeLater(() -> {
                                        messageBuilder.append(token);
                                        commitMessage.setCommitMessage(buildFinalMessage(finalBranchName, messageBuilder.toString()));
                                    }),
                                    error -> ApplicationManager.getApplication().invokeLater(() -> {
                                        IdeaDialogUtil.showError(project, "Error generating commit message: " + error.getMessage(), "Error");
                                    })
                            );
                        } else {
                            String commitMessageFromAi = commitMessageService.generateCommitMessage(diff).trim();
                            ApplicationManager.getApplication().invokeLater(() -> {
                                commitMessage.setCommitMessage(buildFinalMessage(finalBranchName, commitMessageFromAi));
                            });
                        }
                    } catch (IllegalArgumentException ex) {
                        IdeaDialogUtil.showWarning(project, ex.getMessage() + "\n ----Please check your module config.",
                                "AI Commit Message Warning");
                    } catch (Exception ex) {
                        IdeaDialogUtil.showError(project, "Error generating commit message: " + ex.getMessage(), "Error");
                    } finally {
                        update(e);
                    }
                }
            });

        } finally {
            getTemplatePresentation().setEnabled(true);
        }
    }

    private String buildFinalMessage(String branchName, String content) {
        return branchName + ":" + content;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 控制 Action 的启用/禁用状态
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // 指定在后台线程更新 Action 状态，提高性能
        return ActionUpdateThread.BGT;
    }

}