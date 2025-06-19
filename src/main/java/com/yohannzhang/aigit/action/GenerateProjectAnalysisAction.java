package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.core.analysis.BaseCodeAnalyzer;
import com.yohannzhang.aigit.core.llm.LLMEngine;
import com.yohannzhang.aigit.core.llm.LLMEngineFactory;
import com.yohannzhang.aigit.core.models.FileMetadata;
import com.yohannzhang.aigit.core.models.Symbol;
import com.yohannzhang.aigit.service.AnalysisService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenerateProjectAnalysisAction extends AnAction {
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private AnalysisService analysisService;
    private BaseCodeAnalyzer codeAnalyzer;

    public GenerateProjectAnalysisAction() {
        super("生成项目分析报告");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            showError("无法获取项目信息");
            return;
        }

        // 重置取消状态
        isCancelled.set(false);

        // Show tool window and start loading
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow != null) {
            toolWindow.show();
        }

        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
        windowFactory.startLoadingAnimation(project);

        // 获取项目根目录
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            windowFactory.stopLoadingAnimation(project);
            showError("无法获取项目路径");
            return;
        }

        // 获取选中的 LLM 客户端
        ApiKeySettings settings = ApiKeySettings.getInstance();
        String selectedClient = settings.getSelectedClient();
        if (selectedClient == null) {
            windowFactory.stopLoadingAnimation(project);
            showError("请先选择 LLM 客户端");
            return;
        }

        // 创建文档目录
        Path docDir = Paths.get(projectPath, "docs");
        try {
            Files.createDirectories(docDir);
        } catch (IOException ex) {
            windowFactory.stopLoadingAnimation(project);
            showError("创建文档目录失败: " + ex.getMessage());
            return;
        }

        // 生成报告文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path reportFile = docDir.resolve("项目分析报告_" + timestamp + ".md");

        // 创建 LLM 引擎
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(selectedClient);
        if (moduleConfig == null) {
            windowFactory.stopLoadingAnimation(project);
            showError("未找到模块配置");
            return;
        }

        LLMEngine llmEngine = LLMEngineFactory.createEngine(selectedClient, moduleConfig);
        if (llmEngine == null) {
            windowFactory.stopLoadingAnimation(project);
            showError("创建 LLM 引擎失败");
            return;
        }

        // 创建分析服务
        codeAnalyzer = new BaseCodeAnalyzer();
        analysisService = new AnalysisService(codeAnalyzer, llmEngine);

        // 在后台线程中执行项目分析
        ProgressManager.getInstance().run(
            new Task.Backgroundable(project, "生成项目分析报告", true) {
                private volatile boolean isDisposed = false;

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    indicator.setText("正在分析项目...");

                    try {
                        // 分析项目
                        com.yohannzhang.aigit.core.models.Project projectAnalysis = codeAnalyzer.analyzeProject(Paths.get(projectPath));
                        if (projectAnalysis == null) {
                            throw new IllegalStateException("项目分析结果为空");
                        }
                        
                        // 生成分析报告
                        String report = generateAnalysisReport(projectAnalysis);
                        if (report == null || report.trim().isEmpty()) {
                            throw new IllegalStateException("生成的分析报告为空");
                        }
                        
                        // 保存报告
                        try (FileWriter writer = new FileWriter(reportFile.toFile())) {
                            writer.write(report);
                        }

                        // 获取相对路径
                        String relativePath = reportFile.toString()
                                .replace(project.getBasePath(), "")
                                .replaceFirst("^[/\\\\]", "");

                        // 构建成功信息
                        Map<String, Object> stats = projectAnalysis.getStats();
                        String successMessage = String.format(
                                "<div style='padding: 15px; background-color: #e8f5e9; border-left: 4px solid #4caf50; margin: 10px 0;'>" +
                                        "<h3 style='color: #2e7d32; margin-top: 0;'>✨ 项目分析报告生成成功</h3>" +
                                        "<p style='margin: 5px 0;'><strong>📁 保存位置：</strong><code style='background: #f5f5f5; padding: 2px 4px; border-radius: 3px;'>%s</code></p>" +
                                        "<p style='margin: 5px 0;'><strong>📊 分析结果：</strong></p>" +
                                        "<ul style='margin: 5px 0;'>" +
                                        "<li>总文件数：%d</li>" +
                                        "<li>总代码行数：%d</li>" +
                                        "<li>支持的语言：%s</li>" +
                                        "</ul>" +
                                        "<p style='margin: 5px 0;'><strong>💡 提示：</strong>您可以在项目目录的 docs 文件夹中找到完整的分析报告</p>" +
                                        "</div>",
                                relativePath,
                                projectAnalysis.getFiles().size(),
                                stats.getOrDefault("totalLines", 0),
                                stats.get("languages") instanceof Map ? String.join(", ", ((Map<String, Long>) stats.get("languages")).keySet()) : "暂无"
                        );

                        windowFactory.updateResult(successMessage, project);

                    } catch (ProcessCanceledException e) {
                        // 处理取消操作
                        isCancelled.set(true);
                        if (analysisService != null) {
                            analysisService.setCancelled(true);
                        }
                        throw e;
                    } catch (Exception e) {
                        windowFactory.stopLoadingAnimation(project);
                        String errorMessage = String.format(
                                "<div style='padding: 15px; background-color: #ffebee; border-left: 4px solid #f44336; margin: 10px 0;'>" +
                                        "<h3 style='color: #c62828; margin-top: 0;'>❌ 生成分析报告时发生错误</h3>" +
                                        "<p style='margin: 5px 0;'><strong>❓ 错误信息：</strong>%s</p>" +
                                        "<p style='margin: 5px 0;'><strong>💡 提示：</strong>请检查错误信息，修复问题后重试</p>" +
                                        "</div>",
                                e.getMessage()
                        );
                        windowFactory.updateResult(errorMessage, project);
                    } finally {
                        windowFactory.stopLoadingAnimation(project);
                        isDisposed = true;
                    }
                }

                @Override
                public void onCancel() {
                    isCancelled.set(true);
                    if (analysisService != null) {
                        analysisService.setCancelled(true);
                    }
                    isDisposed = true;
                }

                @Override
                public void onFinished() {
                    if (!isDisposed) {
                        isDisposed = true;
                    }
                }
            }
        );
    }

    private void showError(String message) {
        String errorMessage = String.format(
                "<div style='padding: 15px; background-color: #ffebee; border-left: 4px solid #f44336; margin: 10px 0;'>" +
                        "<h3 style='color: #c62828; margin-top: 0;'>❌ 错误</h3>" +
                        "<p style='margin: 5px 0;'><strong>❓ 错误信息：</strong>%s</p>" +
                        "</div>",
                message
        );
        // 这里需要获取当前项目实例来更新UI
        // 暂时只打印错误信息
        System.err.println(errorMessage);
    }

    private String generateAnalysisReport(com.yohannzhang.aigit.core.models.Project project) {
        if (project == null) {
            throw new IllegalArgumentException("项目对象不能为空");
        }

        StringBuilder report = new StringBuilder();
        report.append("# 项目分析报告\n\n");
        report.append("## 1. 项目概述\n\n");
        report.append("- 项目名称：").append(project.getName()).append("\n");
        report.append("- 分析时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        // 添加项目统计信息
        Map<String, Object> stats = project.getStats();
        if (stats == null) {
            stats = new HashMap<>();
        }

        report.append("## 2. 项目统计\n\n");
        report.append("### 2.1 文件统计\n\n");
        report.append("- 总文件数：").append(stats.getOrDefault("totalFiles", 0)).append("\n");
        report.append("- 总代码行数：").append(stats.getOrDefault("totalLines", 0)).append("\n\n");

        // 添加语言统计
        report.append("### 2.2 语言统计\n\n");
        Object languagesObj = stats.get("languages");
        if (languagesObj instanceof Map) {
            Map<String, Long> languages = (Map<String, Long>) languagesObj;
            if (!languages.isEmpty()) {
                for (Map.Entry<String, Long> entry : languages.entrySet()) {
                    report.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append(" 个文件\n");
                }
            } else {
                report.append("暂无语言统计信息\n");
            }
        } else {
            report.append("暂无语言统计信息\n");
        }
        report.append("\n");

        // 添加符号统计
        report.append("### 2.3 符号统计\n\n");
        Object symbolsObj = stats.get("symbols");
        if (symbolsObj instanceof Map) {
            Map<String, Long> symbols = (Map<String, Long>) symbolsObj;
            if (!symbols.isEmpty()) {
                for (Map.Entry<String, Long> entry : symbols.entrySet()) {
                    report.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append(" 个\n");
                }
            } else {
                report.append("暂无符号统计信息\n");
            }
        } else {
            report.append("暂无符号统计信息\n");
        }
        report.append("\n");

        // 添加文件分析
        report.append("## 3. 文件分析\n\n");
        Map<String, FileMetadata> files = project.getFiles();
        if (files != null && !files.isEmpty()) {
            for (Map.Entry<String, FileMetadata> entry : files.entrySet()) {
                FileMetadata metadata = entry.getValue();
                if (metadata == null) continue;

                report.append("### ").append(metadata.getPath()).append("\n\n");
                report.append("- 语言：").append(metadata.getLanguage()).append("\n");
                report.append("- 大小：").append(metadata.getSize()).append(" 字节\n");
                report.append("- 行数：").append(metadata.getLines()).append("\n");
                report.append("- 最后修改：").append(metadata.getLastModified()).append("\n\n");

                // 添加符号信息
                Map<String, List<Symbol>> symbols = metadata.getSymbols();
                if (symbols != null && !symbols.isEmpty()) {
                    report.append("#### 符号列表\n\n");
                    for (Map.Entry<String, List<Symbol>> symbolEntry : symbols.entrySet()) {
                        if (symbolEntry.getValue() == null || symbolEntry.getValue().isEmpty()) continue;
                        
                        report.append("##### ").append(symbolEntry.getKey()).append("\n\n");
                        for (Symbol symbol : symbolEntry.getValue()) {
                            if (symbol == null) continue;
                            
                            report.append("- ").append(symbol.getName())
                                  .append(" (").append(symbol.getVisibility()).append(")\n");
                            if (symbol.getDocumentation() != null && !symbol.getDocumentation().isEmpty()) {
                                report.append("  > ").append(symbol.getDocumentation().replace("\n", "\n  > ")).append("\n");
                            }
                        }
                        report.append("\n");
                    }
                }

                // 添加依赖信息
                List<String> dependencies = metadata.getDependencies();
                if (dependencies != null && !dependencies.isEmpty()) {
                    report.append("#### 依赖列表\n\n");
                    for (String dependency : dependencies) {
                        if (dependency != null && !dependency.trim().isEmpty()) {
                            report.append("- ").append(dependency).append("\n");
                        }
                    }
                    report.append("\n");
                }

                // 添加代码摘要
                String summary = metadata.getSummary();
                if (summary != null && !summary.trim().isEmpty()) {
                    report.append("#### 代码摘要\n\n");
                    report.append(summary).append("\n\n");
                }
            }
        } else {
            report.append("暂无文件分析信息\n\n");
        }

        // 添加代码质量分析
        report.append("## 4. 代码质量分析\n\n");
        if (files != null && !files.isEmpty()) {
            for (Map.Entry<String, FileMetadata> entry : files.entrySet()) {
                FileMetadata metadata = entry.getValue();
                if (metadata == null) continue;

                try {
                    Path filePath = Paths.get(metadata.getPath());
                    if (!Files.exists(filePath)) {
                        report.append("### ").append(metadata.getPath()).append("\n\n");
                        report.append("文件不存在\n\n");
                        continue;
                    }

                    long fileSize = Files.size(filePath);
                    if (fileSize > MAX_FILE_SIZE) {
                        report.append("### ").append(metadata.getPath()).append("\n\n");
                        report.append("文件过大，跳过分析（超过 ").append(MAX_FILE_SIZE / 1024 / 1024).append("MB）\n\n");
                        continue;
                    }

                    String content = Files.readString(filePath);
                    Map<String, Object> quality = codeAnalyzer.analyzeCodeQuality(content, metadata.getLanguage());
                    if (quality == null) {
                        report.append("### ").append(metadata.getPath()).append("\n\n");
                        report.append("无法获取代码质量分析结果\n\n");
                        continue;
                    }
                    
                    report.append("### ").append(metadata.getPath()).append("\n\n");
                    report.append("- 总行数：").append(quality.getOrDefault("totalLines", 0)).append("\n");
                    report.append("- 代码行数：").append(quality.getOrDefault("codeLines", 0)).append("\n");
                    report.append("- 注释行数：").append(quality.getOrDefault("commentLines", 0)).append("\n");
                    
                    // 处理注释比例
                    Object commentRatioObj = quality.get("commentRatio");
                    if (commentRatioObj instanceof Double) {
                        report.append("- 注释比例：").append(String.format("%.2f%%", (Double) commentRatioObj * 100)).append("\n");
                    } else {
                        report.append("- 注释比例：暂无数据\n");
                    }
                    
                    // 处理复杂度信息
                    Object complexityObj = quality.get("complexity");
                    if (complexityObj instanceof Map) {
                        Map<String, Object> complexity = (Map<String, Object>) complexityObj;
                        report.append("- 圈复杂度：").append(complexity.getOrDefault("cyclomaticComplexity", 0)).append("\n");
                        report.append("- 最大嵌套深度：").append(complexity.getOrDefault("maxNestingDepth", 0)).append("\n");
                    } else {
                        report.append("- 圈复杂度：暂无数据\n");
                        report.append("- 最大嵌套深度：暂无数据\n");
                    }
                    
                    // 处理命名问题
                    Object namingIssuesObj = quality.get("namingIssues");
                    if (namingIssuesObj instanceof Number) {
                        report.append("- 命名问题数：").append(namingIssuesObj).append("\n");
                    } else {
                        report.append("- 命名问题数：暂无数据\n");
                    }
                    report.append("\n");
                } catch (IOException e) {
                    report.append("### ").append(metadata.getPath()).append("\n\n");
                    report.append("无法读取文件内容进行质量分析：").append(e.getMessage()).append("\n\n");
                } catch (Exception e) {
                    report.append("### ").append(metadata.getPath()).append("\n\n");
                    report.append("分析过程中发生错误：").append(e.getMessage()).append("\n\n");
                }
            }
        } else {
            report.append("暂无代码质量分析信息\n\n");
        }

        // 添加重复代码分析
        report.append("## 5. 重复代码分析\n\n");
        try {
            List<Map<String, Object>> duplications = codeAnalyzer.analyzeDuplication(project);
            if (duplications != null && !duplications.isEmpty()) {
                for (Map<String, Object> duplication : duplications) {
                    if (duplication == null) continue;
                    
                    report.append("### 重复代码片段\n\n");
                    report.append("- 文件1：").append(duplication.getOrDefault("file1", "未知")).append("\n");
                    report.append("- 文件2：").append(duplication.getOrDefault("file2", "未知")).append("\n");
                    
                    Object similarityObj = duplication.get("similarity");
                    if (similarityObj instanceof Double) {
                        report.append("- 相似度：").append(String.format("%.2f%%", (Double) similarityObj * 100)).append("\n");
                    } else {
                        report.append("- 相似度：暂无数据\n");
                    }
                    report.append("\n");
                }
            } else {
                report.append("未发现重复代码。\n\n");
            }
        } catch (Exception e) {
            report.append("重复代码分析过程中发生错误：").append(e.getMessage()).append("\n\n");
        }

        // 添加依赖关系分析
        report.append("## 6. 依赖关系分析\n\n");
        try {
            Map<String, List<String>> dependencies = codeAnalyzer.analyzeDependencies(project);
            if (dependencies != null && !dependencies.isEmpty()) {
                for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
                    
                    report.append("### ").append(entry.getKey()).append("\n\n");
                    for (String dependency : entry.getValue()) {
                        if (dependency != null && !dependency.trim().isEmpty()) {
                            report.append("- ").append(dependency).append("\n");
                        }
                    }
                    report.append("\n");
                }
            } else {
                report.append("暂无依赖关系分析信息\n\n");
            }
        } catch (Exception e) {
            report.append("依赖关系分析过程中发生错误：").append(e.getMessage()).append("\n\n");
        }

        return report.toString();
    }
} 