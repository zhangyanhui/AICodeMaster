package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.core.analysis.BaseCodeAnalyzer;
import com.yohannzhang.aigit.core.llm.LLMEngine;
import com.yohannzhang.aigit.core.llm.LLMEngine.StreamCallback;
import com.yohannzhang.aigit.core.llm.LLMEngineFactory;
import com.yohannzhang.aigit.service.AnalysisService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GenerateDependencyGraphAction extends AnAction {
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private AnalysisService analysisService;

    public GenerateDependencyGraphAction() {
        super("依赖关系图");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // 重置取消状态
        isCancelled.set(false);

        // Show tool window and start loading
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow != null) {
            toolWindow.show();
        }

        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
        windowFactory.startLoadingAnimation(project);
//        windowFactory.updateResult("正在准备生成项目文档...\n", project);

        // 获取项目根目录
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            windowFactory.stopLoadingAnimation(project);
            windowFactory.updateResult("无法获取项目路径", project);
            return;
        }

        // 获取选中的 LLM 客户端
        ApiKeySettings settings = ApiKeySettings.getInstance();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(settings.getSelectedClient());

        String selectedClient = settings.getSelectedClient();
        if (selectedClient == null) {
            windowFactory.stopLoadingAnimation(project);
            windowFactory.updateResult("请先选择一个 LLM 客户端", project);
            return;
        }

        if (moduleConfig == null) {
            windowFactory.stopLoadingAnimation(project);
            windowFactory.updateResult("未找到模块配置", project);
            return;
        }

        // 创建 LLM 引擎
        LLMEngine llmEngine = LLMEngineFactory.createEngine(selectedClient, moduleConfig);
        if (llmEngine == null) {
            windowFactory.stopLoadingAnimation(project);
            windowFactory.updateResult("创建 LLM 引擎失败", project);
            return;
        }

        // 创建代码分析器
        BaseCodeAnalyzer codeAnalyzer = new BaseCodeAnalyzer();

        // 创建分析服务
        analysisService = new AnalysisService(codeAnalyzer, llmEngine);

        // 收集项目文件
        List<String> projectFiles = collectProjectFiles(new File(projectPath));

        // 创建文档输出目录
        String docDir = projectPath + File.separator + "docs";
        try {
            Files.createDirectories(Paths.get(docDir));
        } catch (IOException ex) {
            windowFactory.stopLoadingAnimation(project);
            windowFactory.updateResult("创建文档目录失败: " + ex.getMessage(), project);
            return;
        }

        // 生成文档文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String docFile = docDir + File.separator + "依赖关系图_" + timestamp + ".md";

        // 生成项目文档
        AtomicReference<StringBuilder> content = new AtomicReference<>(new StringBuilder());

        String promt = analysisService.generateDependencyGraph(projectFiles);
        System.out.println("生成项目文档的promt:"+promt);

        // 在后台线程中执行文档生成
        ProgressManager.getInstance().run(
            new Task.Backgroundable(project, "生成依赖关系图", true) {
                private volatile boolean isDisposed = false;

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    indicator.setText("正在生成项目文档...");
                    
                    try {
                        // 开始生成文档
                        analysisService.generateProjectDocumentation(promt, new StreamCallback() {
                            private FileWriter writer;

                            @Override
                            public void onStart() {
                                content.get().setLength(0);
                                windowFactory.submitButton(project);
//                                windowFactory.updateLoadingProgress(project, "正在初始化文档生成...");
                                try {
                                    writer = new FileWriter(docFile);
                                } catch (IOException ex) {
                                    windowFactory.updateResult("创建文档文件失败: " + ex.getMessage(), project);
                                }
                            }

                            @Override
                            public void onToken(String token) {
                                if (isCancelled.get()) {
                                    try {
                                        if (writer != null) {
                                            writer.close();
                                        }
                                    } catch (IOException ex) {
                                        // 忽略关闭时的错误
                                    }
                                    throw new ProcessCanceledException();
                                }
                                content.get().append(token);
                                try {
                                    if (writer != null) {
                                        writer.write(token);
                                        writer.flush();
                                    }
                                } catch (IOException ex) {
                                    windowFactory.updateResult("写入文档失败: " + ex.getMessage(), project);
                                }
                            }

                            @Override
                            public void onError(Throwable error) {
                                try {
                                    if (writer != null) {
                                        writer.close();
                                        writer = null;
                                    }
                                } catch (IOException ex) {
                                    // 忽略关闭时的错误
                                }

                                // 获取相对路径
                                String relativePath = new File(docFile).getAbsolutePath()
                                    .replace(project.getBasePath(), "")
                                    .replaceFirst("^[/\\\\]", "");

                                if (error instanceof ProcessCanceledException) {
                                    windowFactory.stopLoadingAnimation(project);
                                    String cancelMessage = String.format(
                                        "<div style='padding: 15px; background-color: #fff3e0; border-left: 4px solid #ff9800; margin: 10px 0;'>" +
                                        "<h3 style='color: #e65100; margin-top: 0;'>⚠️ 文档生成已取消</h3>" +
                                        "<p style='margin: 5px 0;'><strong>📁 部分内容已保存至：</strong><code style='background: #f5f5f5; padding: 2px 4px; border-radius: 3px;'>%s</code></p>" +
                                        "<p style='margin: 5px 0;'><strong>💡 提示：</strong>您可以查看已保存的内容，或重新生成完整文档</p>" +
                                        "</div>",
                                        relativePath
                                    );
                                    windowFactory.updateResult(cancelMessage, project);
                                } else {
                                    windowFactory.stopLoadingAnimation(project);
                                    String errorMessage = String.format(
                                        "<div style='padding: 15px; background-color: #ffebee; border-left: 4px solid #f44336; margin: 10px 0;'>" +
                                        "<h3 style='color: #c62828; margin-top: 0;'>❌ 生成文档时发生错误</h3>" +
                                        "<p style='margin: 5px 0;'><strong>📁 部分内容已保存至：</strong><code style='background: #f5f5f5; padding: 2px 4px; border-radius: 3px;'>%s</code></p>" +
                                        "<p style='margin: 5px 0;'><strong>❓ 错误信息：</strong>%s</p>" +
                                        "<p style='margin: 5px 0;'><strong>💡 提示：</strong>请检查错误信息，修复问题后重试</p>" +
                                        "</div>",
                                        relativePath,
                                        error.getMessage()
                                    );
                                    windowFactory.updateResult(errorMessage, project);
                                }
                                windowFactory.resetButton(project);
                            }

                            @Override
                            public void onComplete() {
                                try {
                                    if (writer != null) {
                                        writer.close();
                                        writer = null;
                                    }
                                } catch (IOException ex) {
                                    // 忽略关闭时的错误
                                }

                                if (!isCancelled.get()) {
                                    windowFactory.updateLoadingProgress(project, "文档生成完成，正在保存...");
                                    windowFactory.stopLoadingAnimation(project);
                                    
                                    // 获取相对路径
                                    String relativePath = new File(docFile).getAbsolutePath()
                                        .replace(project.getBasePath(), "")
                                        .replaceFirst("^[/\\\\]", "");
                                    
                                    // 构建成功信息
                                    String successMessage = String.format(
                                        "<div style='padding: 15px; background-color: #e8f5e9; border-left: 4px solid #4caf50; margin: 10px 0;'>" +
                                        "<h3 style='color: #2e7d32; margin-top: 0;'>✨ 文档生成成功</h3>" +
                                        "<p style='margin: 5px 0;'><strong>📁 保存位置：</strong><code style='background: #f5f5f5; padding: 2px 4px; border-radius: 3px;'>%s</code></p>" +
                                        "<p style='margin: 5px 0;'><strong>📝 文件大小：</strong>%.2f KB</p>" +
                                        "<p style='margin: 5px 0;'><strong>⏱️ 生成时间：</strong>%s</p>" +
                                        "<p style='margin: 5px 0;'><strong>💡 提示：</strong>您可以在项目目录的 docs 文件夹中找到生成的文档</p>" +
                                        "</div>",
                                        relativePath,
                                        new File(docFile).length() / 1024.0,
                                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                    );
                                    
                                    windowFactory.updateResult(successMessage, project);
                                    
                                    // 刷新文件系统以显示新文件
                                    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
                                    VirtualFile docDirFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(docDir);
                                    if (docDirFile != null) {
                                        docDirFile.refresh(false, false);
                                    }
                                }
                                windowFactory.resetButton(project);
                            }
                        });
                    } catch (ProcessCanceledException e) {
                        // 处理取消操作
                        isCancelled.set(true);
                        if (analysisService != null) {
                            analysisService.setCancelled(true);
                        }
                        throw e;
                    } finally {
                        isDisposed = true;
                    }
                }

                @Override
                public void onCancel() {
                    isCancelled.set(true);
                    if (analysisService != null) {
                        analysisService.setCancelled(true);
                    }
//                    windowFactory.clearProgressIndicator(project);
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

    private String buildPromt(List<String> projectFiles){
        // 构建提示词
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("请为以下项目生成详细的文档，包括：\n\n");
        promptBuilder.append("1. 项目概述\n");
        promptBuilder.append("   - 项目名称和描述\n");
        promptBuilder.append("   - 主要功能和目标\n");
        promptBuilder.append("   - 技术栈和依赖\n\n");
        promptBuilder.append("2. 项目结构\n");
        promptBuilder.append("   - 目录结构说明\n");
        promptBuilder.append("   - 主要模块和包\n");
        promptBuilder.append("   - 关键文件说明\n\n");
        promptBuilder.append("3. 核心功能\n");
        promptBuilder.append("   - 主要类和接口\n");
        promptBuilder.append("   - 关键算法和实现\n");
        promptBuilder.append("   - 数据流和交互\n\n");
        promptBuilder.append("4. 开发指南\n");
        promptBuilder.append("   - 环境配置\n");
        promptBuilder.append("   - 构建和部署\n");
        promptBuilder.append("   - 测试和调试\n\n");
        promptBuilder.append("5. 维护说明\n");
        promptBuilder.append("   - 代码规范\n");
        promptBuilder.append("   - 常见问题\n");
        promptBuilder.append("   - 扩展建议\n\n");
        promptBuilder.append("项目文件列表：\n");
        for (String file : projectFiles) {
            if (isCancelled.get()) {
                throw new ProcessCanceledException();
            }
            promptBuilder.append("- ").append(file).append("\n");
        }
        return promptBuilder.toString();
    }
    private List<String> collectProjectFiles(File directory) {
        List<String> files = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] children = directory.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory()) {
                        // 跳过 .git, .idea 等目录
                        if (!child.getName().startsWith(".")) {
                            files.addAll(collectProjectFiles(child));
                        }
                    } else {
                        // 只收集源代码文件
                        String name = child.getName().toLowerCase();
                        if (name.endsWith(".java") || name.endsWith(".kt") ||
                                name.endsWith(".py") || name.endsWith(".js") ||
                                name.endsWith(".ts") || name.endsWith(".cpp") ||
                                name.endsWith(".h") || name.endsWith(".cs")) {
                            files.add(child.getAbsolutePath());
                        }
                    }
                }
            }
        }
        return files;
    }
} 