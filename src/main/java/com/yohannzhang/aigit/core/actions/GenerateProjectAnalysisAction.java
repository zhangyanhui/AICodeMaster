package com.yohannzhang.aigit.core.actions;

import com.yohannzhang.aigit.core.analysis.BaseCodeAnalyzer;
import com.yohannzhang.aigit.core.analysis.CodeAnalyzer;
import com.yohannzhang.aigit.core.models.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class GenerateProjectAnalysisAction implements Action {
    private final CodeAnalyzer codeAnalyzer;
    private final Path projectPath;

    public GenerateProjectAnalysisAction(Path projectPath) {
        this.codeAnalyzer = new BaseCodeAnalyzer();
        this.projectPath = projectPath;
    }

    @Override
    public ActionResult execute() {
        try {
            // 分析项目
            Project project = codeAnalyzer.analyzeProject(projectPath);
            
            // 生成分析报告
            Map<String, Object> report = generateAnalysisReport(project);
            
            return new ActionResult(true, "项目分析完成", report);
        } catch (Exception e) {
            return new ActionResult(false, "项目分析失败: " + e.getMessage(), null);
        }
    }

    private Map<String, Object> generateAnalysisReport(Project project) {
        Map<String, Object> report = new HashMap<>();
        Map<String, Object> summary = new HashMap<>();
        
        // 基础统计
        int totalFiles = project.getFiles().size();
        int totalLines = 0;
        int totalCommentLines = 0;
        int totalMethods = 0;
        int totalClasses = 0;
        int totalComplexity = 0;
        
        // 语言统计
        Map<String, Integer> languageStats = new HashMap<>();
        Map<String, Integer> complexityByLanguage = new HashMap<>();
        
        // 分析每个文件
        for (Map.Entry<String, FileMetadata> entry : project.getFiles().entrySet()) {
            FileMetadata metadata = entry.getValue();
            String language = metadata.getLanguage();
            
            // 更新基础统计
            totalLines += metadata.getLines();
            totalCommentLines += countCommentLines(metadata);
            totalComplexity += calculateFileComplexity(metadata);
            
            // 更新语言统计
            languageStats.merge(language, 1, Integer::sum);
            complexityByLanguage.merge(language, calculateFileComplexity(metadata), Integer::sum);
            
            // 统计方法和类
            Map<String, List<Symbol>> symbols = metadata.getSymbols();
            if (symbols.containsKey("method")) {
                totalMethods += symbols.get("method").size();
            }
            if (symbols.containsKey("class")) {
                totalClasses += symbols.get("class").size();
            }
        }
        
        // 计算平均值和比率
        double avgComplexity = totalFiles > 0 ? (double) totalComplexity / totalFiles : 0;
        double commentRatio = totalLines > 0 ? (double) totalCommentLines / totalLines : 0;
        double methodsPerClass = totalClasses > 0 ? (double) totalMethods / totalClasses : 0;
        
        // 设置汇总信息
        summary.put("totalFiles", totalFiles);
        summary.put("totalLines", totalLines);
        summary.put("totalCommentLines", totalCommentLines);
        summary.put("commentRatio", commentRatio);
        summary.put("totalMethods", totalMethods);
        summary.put("totalClasses", totalClasses);
        summary.put("methodsPerClass", methodsPerClass);
        summary.put("totalComplexity", totalComplexity);
        summary.put("avgComplexity", avgComplexity);
        summary.put("languageStats", languageStats);
        summary.put("complexityByLanguage", complexityByLanguage);
        
        // 添加质量评级
        String qualityRating = calculateQualityRating(avgComplexity, commentRatio, methodsPerClass);
        summary.put("qualityRating", qualityRating);
        
        // 添加改进建议
        List<String> recommendations = generateRecommendations(summary);
        summary.put("recommendations", recommendations);
        
        // 构建最终报告
        report.put("summary", summary);
        report.put("generatedAt", LocalDateTime.now().toString());
        
        return report;
    }

    private int countCommentLines(FileMetadata metadata) {
        int commentLines = 0;
        String content = metadata.getContent();
        if (content != null) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.trim().startsWith("//") || 
                    line.trim().startsWith("/*") || 
                    line.trim().startsWith("*") || 
                    line.trim().startsWith("#")) {
                    commentLines++;
                }
            }
        }
        return commentLines;
    }

    private int calculateFileComplexity(FileMetadata metadata) {
        int complexity = 0;
        Map<String, List<Symbol>> symbols = metadata.getSymbols();
        if (symbols.containsKey("method")) {
            for (Symbol method : symbols.get("method")) {
                complexity += calculateMethodComplexity(method);
            }
        }
        return complexity;
    }

    private int calculateMethodComplexity(Symbol method) {
        int complexity = 1; // 基础复杂度
        String content = method.getContent();
        if (content != null) {
            // 计算控制流语句
            complexity += countOccurrences(content, "if") + 
                         countOccurrences(content, "for") + 
                         countOccurrences(content, "while") + 
                         countOccurrences(content, "switch") + 
                         countOccurrences(content, "case") + 
                         countOccurrences(content, "catch") + 
                         countOccurrences(content, "&&") + 
                         countOccurrences(content, "||");
        }
        return complexity;
    }

    private int countOccurrences(String content, String pattern) {
        return (content.length() - content.replace(pattern, "").length()) / pattern.length();
    }

    private String calculateQualityRating(double avgComplexity, double commentRatio, double methodsPerClass) {
        int score = 100;
        
        // 圈复杂度评分 (0-40分)
        if (avgComplexity > 20) score -= 40;
        else if (avgComplexity > 15) score -= 30;
        else if (avgComplexity > 10) score -= 20;
        else if (avgComplexity > 5) score -= 10;
        
        // 注释比例评分 (0-30分)
        if (commentRatio < 0.1) score -= 30;
        else if (commentRatio < 0.2) score -= 20;
        else if (commentRatio < 0.3) score -= 10;
        
        // 方法分布评分 (0-30分)
        if (methodsPerClass > 20) score -= 30;
        else if (methodsPerClass > 15) score -= 20;
        else if (methodsPerClass > 10) score -= 10;
        
        // 返回评级
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    private List<String> generateRecommendations(Map<String, Object> summary) {
        List<String> recommendations = new ArrayList<>();
        
        double avgComplexity = (double) summary.get("avgComplexity");
        double commentRatio = (double) summary.get("commentRatio");
        double methodsPerClass = (double) summary.get("methodsPerClass");
        
        // 圈复杂度建议
        if (avgComplexity > 15) {
            recommendations.add("建议重构高复杂度的代码，将复杂方法拆分为更小的函数");
        } else if (avgComplexity > 10) {
            recommendations.add("考虑优化部分复杂方法的实现");
        }
        
        // 注释建议
        if (commentRatio < 0.1) {
            recommendations.add("代码注释严重不足，建议增加必要的文档注释");
        } else if (commentRatio < 0.2) {
            recommendations.add("建议为关键代码添加更多注释");
        }
        
        // 方法分布建议
        if (methodsPerClass > 15) {
            recommendations.add("类中的方法数量过多，建议考虑拆分或重构");
        } else if (methodsPerClass > 10) {
            recommendations.add("建议检查类的方法分布，考虑是否需要重构");
        }
        
        // 语言相关建议
        Map<String, Integer> complexityByLanguage = (Map<String, Integer>) summary.get("complexityByLanguage");
        complexityByLanguage.forEach((language, complexity) -> {
            if (complexity > 100) {
                recommendations.add(String.format("%s语言的代码复杂度较高，建议进行重构", language));
            }
        });
        
        return recommendations;
    }

    @Override
    public String getDescription() {
        return "生成项目代码质量分析报告";
    }

    @Override
    public String getActionType() {
        return "PROJECT_ANALYSIS";
    }
} 