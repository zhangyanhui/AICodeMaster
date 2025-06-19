package com.yohannzhang.aigit.core.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.yohannzhang.aigit.core.analyzers.BaseCodeAnalyzer;
import com.yohannzhang.aigit.core.models.ProjectMetadata;
import com.yohannzhang.aigit.core.services.CodeRAGService;
import com.yohannzhang.aigit.utils.NotificationUtils;

import java.nio.file.Paths;

public class CodeRAGAction extends AnAction {
    private final CodeRAGService ragService;
    private final BaseCodeAnalyzer codeAnalyzer;

    public CodeRAGAction() {
        this.ragService = new CodeRAGService();
        this.codeAnalyzer = new BaseCodeAnalyzer();
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        if (project == null) {
            NotificationUtils.showError("No project found");
            return;
        }

        // 获取用户查询
        String query = Messages.showInputDialog(
            project,
            "Enter your code-related question:",
            "Code RAG Search",
            Messages.getQuestionIcon(),
            "",
            null
        );

        if (query == null || query.trim().isEmpty()) {
            return;
        }

        try {
            // 分析项目
            ProjectMetadata projectMetadata = codeAnalyzer.analyzeProject(
                Paths.get(project.getBasePath())
            );

            // 初始化 RAG 服务
            ragService.initializeProject(projectMetadata);

            // 搜索相关代码
            String context = ragService.generateContext(query);

            // 显示结果
            Messages.showInfoMessage(
                project,
                context,
                "Code Search Results"
            );

        } catch (Exception ex) {
            NotificationUtils.showError("Error during code search: " + ex.getMessage());
        } finally {
            // 清理资源
            ragService.clear();
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        e.getPresentation().setEnabledAndVisible(project != null);
    }
} 