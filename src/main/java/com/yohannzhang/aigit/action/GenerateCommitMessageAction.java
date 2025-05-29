package com.yohannzhang.aigit.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.CommitMessageService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import com.yohannzhang.aigit.util.GItCommitUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Action 类，用于生成 Git commit 消息
 * 继承自 AnAction 以集成到 IDEA 的操作系统中
 */
public class GenerateCommitMessageAction extends AnAction {
    private static final String DEFAULT_TEXT = "Generate AI Commit Message";
    private static final String PROCESSING_TEXT = "Stop Generation";
    private volatile boolean isProcessing = false;
    private final AtomicReference<ProgressIndicator> currentIndicator = new AtomicReference<>();
    private final AtomicReference<CommitMessage> currentCommitMessage = new AtomicReference<>();
    
    // 定义默认图标和处理中图标
    private static final Icon DEFAULT_ICON = IconLoader.getIcon("/icons/git-commit-logo.svg", GenerateCommitMessageAction.class);
    private static final Icon PROCESSING_ICON = AllIcons.Actions.StopRefresh;

    public GenerateCommitMessageAction() {
        super(DEFAULT_TEXT, "Generate commit message using AI", DEFAULT_ICON);
    }

    /**
     * 获取CommitMessage对象
     */
    private CommitMessage getCommitMessage(AnActionEvent e) {
        return (CommitMessage) e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL);
    }

    private void updateButtonUI(AnActionEvent e) {
        if (e.getInputEvent() != null && e.getInputEvent().getSource() instanceof ActionButton) {
            SwingUtilities.invokeLater(() -> {
                ActionButton button = (ActionButton) e.getInputEvent().getSource();
                button.repaint();
                button.updateUI();
            });
        }

    }

    private void setProcessingState(AnActionEvent e, boolean processing) {
        isProcessing = processing;
        ApplicationManager.getApplication().invokeLater(() -> {
            // 更新事件的presentation
            Presentation presentation = e.getPresentation();
            presentation.setDescription(processing ? PROCESSING_TEXT : DEFAULT_TEXT);
            presentation.setText(processing ? PROCESSING_TEXT : DEFAULT_TEXT);
            presentation.setIcon(processing ? PROCESSING_ICON : DEFAULT_ICON);
            presentation.setEnabled(true);

            // 更新模板presentation
            Presentation templatePresentation = getTemplatePresentation();
            templatePresentation.setDescription(processing ? PROCESSING_TEXT : DEFAULT_TEXT);
            templatePresentation.setText(processing ? PROCESSING_TEXT : DEFAULT_TEXT);
            templatePresentation.setIcon(processing ? PROCESSING_ICON : DEFAULT_ICON);
            templatePresentation.setEnabled(true);

            updateButtonUI(e);
        });
    }

    private void restoreButtonState(AnActionEvent e, boolean clearOutput) {
        ApplicationManager.getApplication().invokeLater(() -> {
            isProcessing = false;
            currentIndicator.set(null);

            // 如果需要清空输出
            if (clearOutput && currentCommitMessage.get() != null) {
                currentCommitMessage.get().setCommitMessage("");
            }
            currentCommitMessage.set(null);

            // 更新事件的presentation
            Presentation presentation = e.getPresentation();
            presentation.setDescription(DEFAULT_TEXT);
            presentation.setText(DEFAULT_TEXT);
            presentation.setIcon(DEFAULT_ICON);
            presentation.setEnabled(true);

            // 更新模板presentation
            Presentation templatePresentation = getTemplatePresentation();
            templatePresentation.setDescription(DEFAULT_TEXT);
            templatePresentation.setText(DEFAULT_TEXT);
            templatePresentation.setIcon(DEFAULT_ICON);
            templatePresentation.setEnabled(true);

            // 触发 update 方法刷新整体状态
            GenerateCommitMessageAction.this.update(e);

            // 额外的UI更新以确保图标恢复
            SwingUtilities.invokeLater(() -> {
                if (e.getPresentation().getIcon() != DEFAULT_ICON) {
                    GenerateCommitMessageAction.this.update(e); // 再次触发更新
                    updateButtonUI(e);
                }
            });
        });
    }


    @Override
    public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
        // 在action执行前更新状态
        Project project = e.getProject();
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(project != null);
        presentation.setText(isProcessing ? PROCESSING_TEXT : DEFAULT_TEXT);
        presentation.setIcon(isProcessing ? PROCESSING_ICON : DEFAULT_ICON);
    }

    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // 如果正在处理中，则取消当前操作
        if (isProcessing) {
            ProgressIndicator indicator = currentIndicator.get();
            if (indicator != null) {
                indicator.cancel();
                // 立即恢复按钮状态，并清空输出
                restoreButtonState(e, true);
            }
            return;
        }

        // 设置按钮为处理中状态
        setProcessingState(e, true);

        try {
            CommitMessageService commitMessageService = new CommitMessageService();

            if (!commitMessageService.checkNecessaryModuleConfigIsRight()) {
                IdeaDialogUtil.handleModuleNecessaryConfigIsWrong(project);
                restoreButtonState(e, false);
                return;
            }

            AbstractCommitWorkflowHandler<?, ?> commitWorkflowHandler = (AbstractCommitWorkflowHandler<?, ?>) e.getData(
                    VcsDataKeys.COMMIT_WORKFLOW_HANDLER);
            if (commitWorkflowHandler == null) {
                IdeaDialogUtil.handleNoChangesSelected(project);
                restoreButtonState(e, false);
                return;
            }

            GitRepositoryManager gitRepositoryManager = GitRepositoryManager.getInstance(project);
            List<GitRepository> repositories = gitRepositoryManager.getRepositories();
            if (repositories.isEmpty()) {
                IdeaDialogUtil.showWarning(project, "No Git repository found.", "Git Repository Missing");
                restoreButtonState(e, false);
                return;
            }
            GitLocalBranch branch = repositories.get(0).getCurrentBranch();
            if (branch == null) {
                IdeaDialogUtil.showWarning(project, "Current branch is null.", "Git Branch Error");
                restoreButtonState(e, false);
                return;
            }

            CommitMessage commitMessage = getCommitMessage(e);
            currentCommitMessage.set(commitMessage);

            List<Change> includedChanges = commitWorkflowHandler.getUi().getIncludedChanges();
            List<FilePath> includedUnversionedFiles = commitWorkflowHandler.getUi().getIncludedUnversionedFiles();

            if (includedChanges.isEmpty() && includedUnversionedFiles.isEmpty()) {
                commitMessage.setCommitMessage(Constants.NO_FILE_SELECTED);
                restoreButtonState(e, false);
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
                    currentIndicator.set(indicator);
                    
                    try {
                        if (commitMessageService.generateByStream()) {
                            StringBuilder messageBuilder = new StringBuilder();
                            AtomicBoolean streamCompleted = new AtomicBoolean(false);
                            
                            commitMessageService.generateCommitMessageStream(
                                    diff,
                                    token -> {
                                        if (indicator.isCanceled()) {
                                            throw new RuntimeException("Generation cancelled by user");
                                        }
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            messageBuilder.append(token);
                                            commitMessage.setCommitMessage(buildFinalMessage(finalBranchName, messageBuilder.toString()));
                                        });
                                    },
                                    error -> ApplicationManager.getApplication().invokeLater(() -> {
                                        if (!(error instanceof RuntimeException && error.getMessage().equals("Generation cancelled by user"))) {
                                            IdeaDialogUtil.showError(project, "Error generating commit message: " + error.getMessage(), "Error");
                                        }
                                        restoreButtonState(e, error instanceof RuntimeException && error.getMessage().equals("Generation cancelled by user"));
                                    }),
                                    () -> {
                                        streamCompleted.set(true);
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            if (!indicator.isCanceled()) {
                                                // 确保在流式生成完成时恢复按钮状态
                                                restoreButtonState(e, false);
                                                // 强制更新UI
                                                SwingUtilities.invokeLater(() -> {

                                                    e.getPresentation().setIcon(DEFAULT_ICON);
                                                    updateButtonUI(e);

                                                });
                                            }
                                        });
                                    }
                            );
                            
                            // 等待流式生成完成或取消
                            while (!streamCompleted.get() && !indicator.isCanceled()) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            
                            // 确保在完成后再次检查状态
                            if (streamCompleted.get() && !indicator.isCanceled()) {
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    restoreButtonState(e, false);
                                    // 强制更新UI
                                    SwingUtilities.invokeLater(() -> {
                                        if (e.getPresentation().getIcon() != DEFAULT_ICON) {
                                            e.getPresentation().setIcon(DEFAULT_ICON);
                                            updateButtonUI(e);
                                        }
                                    });
                                });
                            }
                        } else {
                            if (!indicator.isCanceled()) {
                                String commitMessageFromAi = commitMessageService.generateCommitMessage(diff).trim();
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    commitMessage.setCommitMessage(buildFinalMessage(finalBranchName, commitMessageFromAi));
                                    restoreButtonState(e, false);
                                    // 强制更新UI
                                    SwingUtilities.invokeLater(() -> {
                                        if (e.getPresentation().getIcon() != DEFAULT_ICON) {
                                            e.getPresentation().setIcon(DEFAULT_ICON);
                                            updateButtonUI(e);
                                        }
                                    });
                                });
                            }
                        }
                    } catch (IllegalArgumentException ex) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            IdeaDialogUtil.showWarning(project, ex.getMessage() + "\n ----Please check your module config.",
                                    "AI Commit Message Warning");
                            restoreButtonState(e, false);
                        });
                    } catch (Exception ex) {
                        if (!(ex instanceof RuntimeException && ex.getMessage().equals("Generation cancelled by user"))) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                IdeaDialogUtil.showError(project, "Error generating commit message: " + ex.getMessage(), "Error");
                                restoreButtonState(e, false);
                                // 强制更新UI
                                SwingUtilities.invokeLater(() -> {
                                    if (e.getPresentation().getIcon() != DEFAULT_ICON) {
                                        e.getPresentation().setIcon(DEFAULT_ICON);
                                        updateButtonUI(e);
                                    }
                                });
                            });
                        } else {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                restoreButtonState(e, true);
                                // 强制更新UI
                                SwingUtilities.invokeLater(() -> {
                                    if (e.getPresentation().getIcon() != DEFAULT_ICON) {
                                        e.getPresentation().setIcon(DEFAULT_ICON);
                                        updateButtonUI(e);
                                    }
                                });
                            });
                        }
                    }
                }
            });
        } catch (Exception ex) {
            restoreButtonState(e, false);
            IdeaDialogUtil.showError(project, "Error: " + ex.getMessage(), "Error");
        }
    }

    private String buildFinalMessage(String branchName, String content) {
        return branchName + ":" + content;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Presentation presentation = e.getPresentation();
        
        // 根据处理状态设置按钮文本和状态
        presentation.setText(isProcessing ? PROCESSING_TEXT : DEFAULT_TEXT);
        presentation.setDescription(isProcessing ? PROCESSING_TEXT : DEFAULT_TEXT);
        presentation.setIcon(isProcessing ? PROCESSING_ICON : DEFAULT_ICON);
        presentation.setEnabled(project != null);
        presentation.setVisible(project != null);

        // 强制刷新UI
        updateButtonUI(e);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}