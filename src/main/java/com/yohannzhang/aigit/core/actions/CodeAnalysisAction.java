package com.yohannzhang.aigit.core.actions;

import com.yohannzhang.aigit.core.analysis.BaseCodeAnalyzer;
import com.yohannzhang.aigit.core.analysis.CodeAnalyzer;
import com.yohannzhang.aigit.core.models.Project;
import com.yohannzhang.aigit.core.models.Action;
import com.yohannzhang.aigit.core.models.ActionResult;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class CodeAnalysisAction implements Action {
    private final CodeAnalyzer codeAnalyzer;
    private final Path projectPath;

    public CodeAnalysisAction(Path projectPath) {
        this.codeAnalyzer = new BaseCodeAnalyzer();
        this.projectPath = projectPath;
    }

    @Override
    public ActionResult execute() {
        try {
            // 分析项目
            Project project = codeAnalyzer.analyzeProject(projectPath);
            
            // 生成代码质量报告
            Map<String, Object> qualityReport = codeAnalyzer.generateCodeQualityReport(project);
            
            // 构建结果
            Map<String, Object> result = new HashMap<>();
            result.put("project", project);
            result.put("qualityReport", qualityReport);
            result.put("analysisTime", LocalDateTime.now().toString());
            
            return new ActionResult(true, "代码分析完成", result);
        } catch (Exception e) {
            return new ActionResult(false, "代码分析失败: " + e.getMessage(), null);
        }
    }

    @Override
    public String getDescription() {
        return "分析项目代码质量并生成报告";
    }

    @Override
    public String getActionType() {
        return "CODE_ANALYSIS";
    }
} 