package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.core.analysis.BaseCodeAnalyzer;
import com.yohannzhang.aigit.core.llm.LLMEngine;
import com.yohannzhang.aigit.core.llm.LLMEngineFactory;
import com.yohannzhang.aigit.service.AnalysisService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import com.yohannzhang.aigit.core.llm.LLMEngine.StreamCallback;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenerateBilingualDocAction extends AnAction {
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private AnalysisService analysisService;

    public GenerateBilingualDocAction() {
        super("中英双语文档");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // 重置取消状态
        isCancelled.set(false);

        // 获取工具窗口并显示
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow == null) return;

        // 立即显示工具窗口并开始动画
        toolWindow.show(() -> {
            startLoadingAnimation(project);

            // 获取项目根目录
            String projectPath = project.getBasePath();
            if (projectPath == null) {
                stopLoadingAnimation(project);
                CombinedWindowFactory.getInstance(project).updateResult("无法获取项目路径", project);
                return;
            }

            // 获取选中的 LLM 客户端
            ApiKeySettings settings = ApiKeySettings.getInstance();
            ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(settings.getSelectedClient());

            String selectedClient = settings.getSelectedClient();
            if (selectedClient == null) {
                stopLoadingAnimation(project);
                CombinedWindowFactory.getInstance(project).updateResult("请先选择一个 LLM 客户端", project);
                return;
            }

            if (moduleConfig == null) {
                stopLoadingAnimation(project);
                CombinedWindowFactory.getInstance(project).updateResult("未找到模块配置", project);
                return;
            }

            // 创建 LLM 引擎
            LLMEngine llmEngine = LLMEngineFactory.createEngine(selectedClient, moduleConfig);
            if (llmEngine == null) {
                stopLoadingAnimation(project);
                CombinedWindowFactory.getInstance(project).updateResult("创建 LLM 引擎失败", project);
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
                stopLoadingAnimation(project);
                CombinedWindowFactory.getInstance(project).updateResult("创建文档目录失败: " + ex.getMessage(), project);
                return;
            }

            // 生成文档文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String docFile = docDir + File.separator + "中英双语文档_" + timestamp + ".md";

            // 在后台线程中执行文档生成
            ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "生成双语文档", true) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setIndeterminate(true);
                        indicator.setText("正在生成双语文档...");
                        
                        // 生成双语文档
                        AtomicReference<StringBuilder> content = new AtomicReference<>(new StringBuilder());
                        LLMEngine.StreamCallback callback = new LLMEngine.StreamCallback() {
                            private FileWriter writer;

                            @Override
                            public void onStart() {
                                content.get().setLength(0);
                                CombinedWindowFactory.getInstance(project).submitButton(project);
                                try {
                                    writer = new FileWriter(docFile);
                                } catch (IOException ex) {
                                    CombinedWindowFactory.getInstance(project).updateResult("创建文档文件失败: " + ex.getMessage(), project);
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
                                    CombinedWindowFactory.getInstance(project).updateResult("写入文档失败: " + ex.getMessage(), project);
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
                                    stopLoadingAnimation(project);
                                    String cancelMessage = String.format(
                                        "<div style='padding: 15px; background-color: #fff3e0; border-left: 4px solid #ff9800; margin: 10px 0;'>" +
                                        "<h3 style='color: #e65100; margin-top: 0;'>⚠️ 文档生成已取消</h3>" +
                                        "<p style='margin: 5px 0;'><strong>📁 部分内容已保存至：</strong><code style='background: #f5f5f5; padding: 2px 4px; border-radius: 3px;'>%s</code></p>" +
                                        "<p style='margin: 5px 0;'><strong>💡 提示：</strong>您可以查看已保存的内容，或重新生成完整文档</p>" +
                                        "</div>",
                                        relativePath
                                    );
                                    CombinedWindowFactory.getInstance(project).updateResult(cancelMessage, project);
                                } else {
                                    stopLoadingAnimation(project);
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
                                    CombinedWindowFactory.getInstance(project).updateResult(errorMessage, project);
                                }
                                CombinedWindowFactory.getInstance(project).resetButton(project);
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
                                    stopLoadingAnimation(project);
                                    
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
                                    
                                    CombinedWindowFactory.getInstance(project).updateResult(successMessage, project);
                                    
                                    // 刷新文件系统以显示新文件
                                    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
                                    VirtualFile docDirFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(docDir);
                                    if (docDirFile != null) {
                                        docDirFile.refresh(false, false);
                                    }
                                }
                                CombinedWindowFactory.getInstance(project).resetButton(project);
                            }
                        };

                        analysisService.generateBilingualDocumentation(projectFiles, callback);
                    }

                    @Override
                    public void onCancel() {
                        isCancelled.set(true);
//                        stopLoadingAnimation(project);
//                        CombinedWindowFactory.getInstance(project).clearProgressIndicator(project);
                    }
                }
            );
        });
    }

    private void startLoadingAnimation(Project project) {
        CombinedWindowFactory.getInstance(project).startLoadingAnimation(project);
    }

    private void stopLoadingAnimation(Project project) {
        CombinedWindowFactory.getInstance(project).stopLoadingAnimation(project);
    }

    private List<String> collectProjectFiles(File directory) {
        List<String> files = new ArrayList<>();
        if (directory.exists() && directory.isDirectory()) {
            File[] fileList = directory.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isDirectory()) {
                        files.addAll(collectProjectFiles(file));
                    } else if (file.isFile() && file.getName().endsWith(".java")) {
                        files.add(file.getAbsolutePath());
                    }
                }
            }
        }
        return files;
    }
} 