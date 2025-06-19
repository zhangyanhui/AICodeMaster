package com.yohannzhang.aigit.services;

import com.intellij.openapi.project.Project;
import com.yohannzhang.aigit.core.analysis.BaseCodeAnalyzer;
import com.yohannzhang.aigit.core.llm.LLMEngine;
import com.yohannzhang.aigit.core.llm.LLMEngine.StreamCallback;
import com.yohannzhang.aigit.core.models.FileMetadata;
import com.yohannzhang.aigit.core.models.ProjectModel;
import com.yohannzhang.aigit.core.models.Symbol;

import java.nio.file.Path;
import java.util.*;

public class AnalysisService {
    private final BaseCodeAnalyzer codeAnalyzer;
    private final LLMEngine llmEngine;
    private final Map<String, Project> projectCache;

    public AnalysisService(BaseCodeAnalyzer codeAnalyzer, LLMEngine llmEngine) {
        this.codeAnalyzer = codeAnalyzer;
        this.llmEngine = llmEngine;
        this.projectCache = new HashMap<>();
    }

    /**
     * 分析项目并缓存结果
     * @param projectPath 项目路径
     * @return 项目ID
     */


    /**
     * 分析业务需求
     * @param projectId 项目ID
     * @param requirement 需求描述
     * @return 分析结果
     */
//    public Map<String, Object> analyzeRequirement(String projectId, String requirement) {
//        ProjectModel project = getProject(projectId);
//
//        // 构建上下文信息
//        StringBuilder context = new StringBuilder();
//        context.append("Project: ").append(project.getName()).append("\n");
//        context.append("Total Files: ").append(project.getFiles().size()).append("\n");
//        context.append("Languages: ").append(project.getStats().get("languages")).append("\n\n");
//
//        // 添加项目结构信息
//        context.append("Project Structure:\n");
//        for (FileMetadata file : project.getFiles().values()) {
//            context.append("- ").append(file.getPath())
//                  .append(" (").append(file.getLanguage()).append(")\n");
//        }
//
//        // 使用LLM分析需求
//        String analysis = llmEngine.generateText(
//            "Analyze the following requirement in the context of this project:\n" + requirement,
//            context.toString()
//        );
//
//        // 解析分析结果
//        Map<String, Object> result = new HashMap<>();
//        result.put("analysis", analysis);
//        result.put("projectContext", context.toString());
//        result.put("requirement", requirement);
//
//        // 添加相关文件信息
//        List<Map<String, Object>> relevantFiles = new ArrayList<>();
//        for (FileMetadata file : project.getFiles().values()) {
//            if (isFileRelevant(file, requirement)) {
//                Map<String, Object> fileInfo = new HashMap<>();
//                fileInfo.put("path", file.getPath());
//                fileInfo.put("language", file.getLanguage());
//                fileInfo.put("summary", file.getSummary());
//                relevantFiles.add(fileInfo);
//            }
//        }
//        result.put("relevantFiles", relevantFiles);
//
//        return result;
//    }

    /**
     * 生成实现方案
     * @param projectId 项目ID
     * @param requirement 需求描述
     * @return 实现方案
     */
//    public Map<String, Object> generateImplementationPlan(String projectId, String requirement) {
//        ProjectModel project = getProject(projectId);
//
//        // 获取需求分析结果
//        Map<String, Object> analysis = analyzeRequirement(projectId, requirement);
//
//        // 构建上下文信息
//        StringBuilder context = new StringBuilder();
//        context.append("Project Analysis:\n").append(analysis.get("analysis")).append("\n\n");
//        context.append("Relevant Files:\n");
//        for (Map<String, Object> file : (List<Map<String, Object>>) analysis.get("relevantFiles")) {
//            context.append("- ").append(file.get("path"))
//                  .append(": ").append(file.get("summary")).append("\n");
//        }
//
//        // 使用LLM生成实现方案
//        String plan = llmEngine.generateText(
//            "Generate an implementation plan for the following requirement:\n" + requirement,
//            context.toString()
//        );
//
//        // 解析实现方案
//        Map<String, Object> result = new HashMap<>();
//        result.put("plan", plan);
//        result.put("analysis", analysis);
//
//        // 添加技术栈建议
//        Map<String, Object> techStack = new HashMap<>();
//        techStack.put("languages", project.getStats().get("languages"));
////        techStack.put("frameworks", suggestFrameworks(project, requirement));
//        result.put("techStack", techStack);
//
//        return result;
//    }

    /**
     * 生成代码
     * @param projectId 项目ID
     * @param requirement 需求描述
     * @param contextFiles 上下文文件列表
     * @param language 目标语言
     * @return 生成的代码
     */
//    public String generateCode(String projectId, String requirement, List<String> contextFiles, String language) {
//        Project project = getProject(projectId);
//
//        // 获取实现方案
//        Map<String, Object> plan = generateImplementationPlan(projectId, requirement);
//
//        // 构建上下文信息
//        StringBuilder context = new StringBuilder();
//        context.append("Implementation Plan:\n").append(plan.get("plan")).append("\n\n");
//
//        // 添加相关文件内容
//        context.append("Context Files:\n");
//        for (String filePath : contextFiles) {
//            FileMetadata file = project.getFiles().get(filePath);
//            if (file != null) {
//                try {
//                    String content = java.nio.file.Files.readString(Path.of(filePath));
//                    context.append("--- ").append(filePath).append(" ---\n");
//                    context.append(content).append("\n\n");
//                } catch (Exception e) {
//                    System.err.println("Error reading file: " + filePath);
//                }
//            }
//        }
//
//        // 使用LLM生成代码
//        return llmEngine.generateCode(
//            "Generate code for the following requirement:\n" + requirement,
//            context.toString(),
//            language
//        );
//    }

    /**
     * 优化代码
     * @param code 源代码
     * @param language 编程语言
     * @param callback 流式输出回调
     */
    public void optimizeCode(String code, String language, StreamCallback callback) {
        try {
            callback.onStart();
            
            // 分析代码质量
            Map<String, Object> qualityMetrics = codeAnalyzer.analyzeCodeQuality(code, language);
            
            // 生成优化建议
            List<String> suggestions = new ArrayList<>();
            
            // 检查圈复杂度
            int cyclomaticComplexity = (int) qualityMetrics.get("cyclomaticComplexity");
            if (cyclomaticComplexity > 10) {
                suggestions.add("将复杂的方法拆分为更小的方法，");
            }
            
            // 检查命名规范
            int namingIssues = (int) qualityMetrics.get("namingIssues");
            if (namingIssues > 0) {
                suggestions.add("修复命名规范问题，");
            }
            
            // 检查注释比例
            double commentRatio = (double) qualityMetrics.get("commentRatio");
            if (commentRatio < 0.1) {
                suggestions.add("添加更多文档注释");
            }

            // 构建上下文信息
            StringBuilder context = new StringBuilder();
            context.append("代码质量分析:\n");
            context.append("- 总行数: ").append(qualityMetrics.get("totalLines")).append("\n");
            context.append("- 代码行数: ").append(qualityMetrics.get("codeLines")).append("\n");
            context.append("- 注释行数: ").append(qualityMetrics.get("commentLines")).append("\n");
            context.append("- 注释比例: ").append(String.format("%.2f", qualityMetrics.get("commentRatio"))).append("\n");
            context.append("- 圈复杂度: ").append(qualityMetrics.get("cyclomaticComplexity")).append("\n");
            context.append("- 命名问题数: ").append(qualityMetrics.get("namingIssues")).append("\n\n");
            
            context.append("优化建议:\n");
            for (String suggestion : suggestions) {
                context.append("- ").append(suggestion).append("\n");
            }

            // 使用LLM优化代码
            llmEngine.optimizeCode(code, language, suggestions, callback);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * 生成测试用例
     * @param code 源代码
     * @param language 编程语言
     * @param callback 流式输出回调
     */
    public void generateTests(String code, String language, StreamCallback callback) {
        try {
            callback.onStart();
            
            // 分析代码结构
            Map<String, Object> codeAnalysis = codeAnalyzer.analyzeCodeStructure(code, language);
            
            // 构建上下文信息
            StringBuilder context = new StringBuilder();
            context.append("代码结构分析:\n");
            context.append("- 类名: ").append(codeAnalysis.get("className")).append("\n");
            context.append("- 方法数: ").append(codeAnalysis.get("methodCount")).append("\n");
            context.append("- 依赖项: ").append(codeAnalysis.get("dependencies")).append("\n\n");
            
            // 使用LLM生成测试用例
            llmEngine.generateTests(code, language, context.toString(), callback);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * 生成文档
     * @param code 源代码
     * @param language 编程语言
     * @return 文档内容
     */
    public String generateDocumentation(String code, String language) {
        return llmEngine.generateDocumentation(code, language);
    }

    /**
     * 获取项目统计信息
     * @param projectId 项目ID
     * @return 统计信息
     */
//    public Map<String, Object> getProjectStats(String projectId) {
//        Project project = getProject(projectId);
//        return project.getStats();
//    }

    /**
     * 获取文件信息
     * @param projectId 项目ID
     * @param filePath 文件路径
     * @return 文件元数据
     */
//    public FileMetadata getFileInfo(String projectId, String filePath) {
//        Project project = getProject(projectId);
//        return project.getFiles().get(filePath);
//    }

    /**
     * 清除项目缓存
     * @param projectId 项目ID
     */
    public void clearProjectCache(String projectId) {
        projectCache.remove(projectId);
    }

    private Project getProject(String projectId) {
        Project project = projectCache.get(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        return project;
    }

    private boolean isFileRelevant(FileMetadata file, String requirement) {
        // 简单的相关性判断
        String[] keywords = requirement.toLowerCase().split("\\s+");
        String fileContent = file.getSummary().toLowerCase();
        
        int matchCount = 0;
        for (String keyword : keywords) {
            if (fileContent.contains(keyword)) {
                matchCount++;
            }
        }
        
        return matchCount >= keywords.length / 2;
    }

//    private List<String> suggestFrameworks(Project project, String requirement) {
//        // 根据项目语言和需求建议框架
//        List<String> frameworks = new ArrayList<>();
//        Set<String> languages = (Set<String>) project.getStats().get("languages");
//
//        if (languages.contains("Java")) {
//            frameworks.add("Spring Boot");
//            frameworks.add("Hibernate");
//        }
//        if (languages.contains("Python")) {
//            frameworks.add("Django");
//            frameworks.add("Flask");
//        }
//        if (languages.contains("JavaScript")) {
//            frameworks.add("React");
//            frameworks.add("Vue.js");
//        }
//
//        return frameworks;
//    }
} 