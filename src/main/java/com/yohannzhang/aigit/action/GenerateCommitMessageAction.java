package com.yohannzhang.aigit.action;

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
import com.intellij.vcsUtil.VcsUtil;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.CommitMessageService;
import com.yohannzhang.aigit.util.GItCommitUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;


import java.util.List;
import java.util.stream.Collectors;

/**
 * Action 类，用于生成 Git commit 消息
 * 继承自 AnAction 以集成到 IDEA 的操作系统中
 */
public class GenerateCommitMessageAction extends AnAction {
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";

    /**
     * 获取CommitMessage对象
     */
    private CommitMessage getCommitMessage(AnActionEvent e) {
        return (CommitMessage) e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL);
    }

    private final StringBuilder messageBuilder = new StringBuilder();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // 根据配置，创建对应的服务
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
        // 获取当前分支
        GitRepositoryManager gitRepositoryManager = GitRepositoryManager.getInstance(project);
        GitLocalBranch branch = gitRepositoryManager.getRepositories().get(0).getCurrentBranch();
        CommitMessage commitMessage = getCommitMessage(e);

        List<Change> includedChanges = commitWorkflowHandler.getUi().getIncludedChanges();
        List<FilePath> includedUnversionedFiles = commitWorkflowHandler.getUi().getIncludedUnversionedFiles().stream()
                .map(file -> VcsUtil.getFilePath(file.getPath()))
                .collect(Collectors.toList());

        if (includedChanges.isEmpty() && includedUnversionedFiles.isEmpty()) {
            commitMessage.setCommitMessage(Constants.NO_FILE_SELECTED);
            return;
        }

        commitMessage.setCommitMessage(branch.getName() + ":" + Constants.GENERATING_COMMIT_MESSAGE);
        String diff = GItCommitUtil.computeDiff(includedChanges, includedUnversionedFiles, project);

        // Run the time-consuming operations in a background task
        ProgressManager.getInstance().run(new Task.Backgroundable(project, Constants.TASK_TITLE, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    System.out.println("diff: " + diff);
                    if (commitMessageService.generateByStream()) {
                        messageBuilder.setLength(0);
                        commitMessageService.generateCommitMessageStream(
                                diff,
                                // onNext 处理每个token
                                token -> ApplicationManager.getApplication().invokeLater(() -> {
                                    if (messageBuilder.length()==0) {
                                        messageBuilder.append(token);
                                        commitMessage.setCommitMessage(token);
                                    } else {
                                        messageBuilder.append(token);
                                        commitMessage.setCommitMessage(branch.getName() + ":" + messageBuilder);
                                    }
                                }),
                                // onError 处理错误
                                error -> ApplicationManager.getApplication().invokeLater(() -> {
                                    IdeaDialogUtil.showError(project, "Error generating commit message: " + error.getMessage(), "Error");
                                })
                        );
                    } else {
                        String commitMessageFromAi = commitMessageService.generateCommitMessage(diff).trim();
                        ApplicationManager.getApplication().invokeLater(() -> {
                            commitMessage.setCommitMessage(branch.getName() + ":" + commitMessageFromAi);
                        });
                    }
                } catch (IllegalArgumentException ex) {
                    IdeaDialogUtil.showWarning(project, ex.getMessage() + "\n ----Please check your module config.",
                            "AI Commit Message Warning");
                } catch (Exception ex) {
                    IdeaDialogUtil.showError(project, "Error generating commit message: " + ex.getMessage(), "Error");
                }
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 控制 Action 的启用/禁用状态
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }
}