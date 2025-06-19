package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.core.llm.LLMEngine;
import com.yohannzhang.aigit.core.llm.LLMEngineFactory;
import com.yohannzhang.aigit.service.ClassDependencyAnalysisService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.Computable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GenerateClassDependencyFlowAction extends AnAction {
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private ClassDependencyAnalysisService analysisService;

    public GenerateClassDependencyFlowAction() {
        super("生成类依赖关系图");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // 获取当前选中的文件或编辑器中的文件
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        
        if (virtualFile == null || !virtualFile.getName().endsWith(".java")) {
            showMessage(project, "请选择一个Java类文件");
            return;
        }

        // 获取PSI文件
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (!(psiFile instanceof PsiJavaFile)) {
            showMessage(project, "请选择一个有效的Java类文件");
            return;
        }

        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        PsiClass[] classes = javaFile.getClasses();
        if (classes.length == 0) {
            showMessage(project, "未在文件中找到类定义");
            return;
        }

        // 如果有多个类，选择第一个或者让用户选择当前光标所在的类
        PsiClass targetClass = findTargetClass(classes, editor, psiFile);
        if (targetClass == null) {
            showMessage(project, "无法确定要分析的类");
            return;
        }

        // 重置取消状态
        isCancelled.set(false);

        // 显示工具窗口并开始加载
        showToolWindow(project);
        
        // 获取项目根目录
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            stopLoadingAndShowMessage(project, "无法获取项目路径");
            return;
        }

        // 获取选中的LLM客户端
        LLMEngine llmEngine = createLLMEngine(project);
        if (llmEngine == null) {
            return;
        }

        // 创建分析服务
        analysisService = new ClassDependencyAnalysisService(llmEngine);

        // 创建文档输出目录
        String docDir = projectPath + File.separator + "docs";
        String docFile = createDocumentFile(docDir, targetClass.getName());
        if (docFile == null) {
            stopLoadingAndShowMessage(project, "创建文档目录失败");
            return;
        }

        // 在后台线程中执行分析
        executeAnalysis(project, targetClass, docFile);
    }

    private PsiClass findTargetClass(PsiClass[] classes, Editor editor, PsiFile psiFile) {
        if (classes.length == 1) {
            return classes[0];
        }

        // 如果有编辑器，尝试找到光标所在的类
        if (editor != null) {
            int offset = editor.getCaretModel().getOffset();
            PsiElement element = psiFile.findElementAt(offset);
            PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (containingClass != null) {
                return containingClass;
            }
        }

        // 默认返回第一个类
        return classes[0];
    }

    private void showToolWindow(Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow != null) {
            toolWindow.show();
        }

        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
        windowFactory.startLoadingAnimation(project);
    }

    private LLMEngine createLLMEngine(Project project) {
        ApiKeySettings settings = ApiKeySettings.getInstance();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(settings.getSelectedClient());

        String selectedClient = settings.getSelectedClient();
        if (selectedClient == null) {
            stopLoadingAndShowMessage(project, "请先选择一个 LLM 客户端");
            return null;
        }

        if (moduleConfig == null) {
            stopLoadingAndShowMessage(project, "未找到模块配置");
            return null;
        }

        LLMEngine llmEngine = LLMEngineFactory.createEngine(selectedClient, moduleConfig);
        if (llmEngine == null) {
            stopLoadingAndShowMessage(project, "创建 LLM 引擎失败");
            return null;
        }

        return llmEngine;
    }

    private String createDocumentFile(String docDir, String className) {
        try {
            Files.createDirectories(Paths.get(docDir));
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            return docDir + File.separator + className + "_依赖关系图_" + timestamp + ".md";
        } catch (IOException ex) {
            return null;
        }
    }

    private void executeAnalysis(Project project, PsiClass targetClass, String docFile) {
        AtomicReference<StringBuilder> content = new AtomicReference<>(new StringBuilder());
        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);

        ProgressManager.getInstance().run(
            new Task.Backgroundable(project, "分析类依赖关系", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    indicator.setText("正在分析类依赖关系...");
                    
                    try {
                        // 在ReadAction中执行PSI操作
                        String analysisResult = ReadAction.compute(() -> {
                            try {
                                return analysisService.analyzeClassDependencies(project, targetClass);
                            } catch (Exception e) {
                                throw new RuntimeException("分析类依赖关系时发生错误: " + e.getMessage(), e);
                            }
                        });
                        
                        // 生成流程图
                        analysisService.generateDependencyFlowChart(analysisResult, new LLMEngine.StreamCallback() {
                            private FileWriter writer;

                            @Override
                            public void onStart() {
                                content.get().setLength(0);
                                windowFactory.submitButton(project);
                                try {
                                    writer = new FileWriter(docFile);
                                } catch (IOException ex) {
                                    windowFactory.updateResult("创建文档文件失败: " + ex.getMessage(), project);
                                }
                            }

                            @Override
                            public void onToken(String token) {
                                if (isCancelled.get()) {
                                    closeWriter();
                                    throw new ProcessCanceledException();
                                }
                                content.get().append(token);
                                writeToFile(token);
                            }

                            @Override
                            public void onError(Throwable error) {
                                closeWriter();
                                handleError(project, error, docFile);
                            }

                            @Override
                            public void onComplete() {
                                closeWriter();
                                handleComplete(project, docFile);
                            }

                            private void writeToFile(String token) {
                                try {
                                    if (writer != null) {
                                        writer.write(token);
                                        writer.flush();
                                    }
                                } catch (IOException ex) {
                                    windowFactory.updateResult("写入文档失败: " + ex.getMessage(), project);
                                }
                            }

                            private void closeWriter() {
                                try {
                                    if (writer != null) {
                                        writer.close();
                                        writer = null;
                                    }
                                } catch (IOException ex) {
                                    // 忽略关闭时的错误
                                }
                            }
                        });
                        
                    } catch (ProcessCanceledException e) {
                        handleCancel(project, docFile);
                    } catch (Exception e) {
                        handleError(project, e, docFile);
                    }
                }

                @Override
                public void onCancel() {
                    isCancelled.set(true);
                    if (analysisService != null) {
                        analysisService.setCancelled(true);
                    }
                }
            }
        );
    }

    private void handleError(Project project, Throwable error, String docFile) {
        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
        
        if (error instanceof ProcessCanceledException) {
            handleCancel(project, docFile);
            return;
        }

        windowFactory.stopLoadingAnimation(project);
        String errorMessage = String.format(
            "<div style='padding: 15px; background-color: #ffebee; border-left: 4px solid #f44336; margin: 10px 0;'>" +
            "<h3 style='color: #c62828; margin-top: 0;'>❌ 分析失败</h3>" +
            "<p style='margin: 5px 0;'><strong>错误信息：</strong>%s</p>" +
            "</div>",
            error.getMessage()
        );
        windowFactory.updateResult(errorMessage, project);
    }

    private void handleCancel(Project project, String docFile) {
        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
        windowFactory.stopLoadingAnimation(project);
        
        String relativePath = new File(docFile).getAbsolutePath()
            .replace(project.getBasePath(), "")
            .replaceFirst("^[/\\\\]", "");

        String cancelMessage = String.format(
            "<div style='padding: 15px; background-color: #fff3e0; border-left: 4px solid #ff9800; margin: 10px 0;'>" +
            "<h3 style='color: #e65100; margin-top: 0;'>⚠️ 分析已取消</h3>" +
            "<p style='margin: 5px 0;'><strong>📁 部分内容已保存至：</strong><code style='background: #f5f5f5; padding: 2px 4px; border-radius: 3px;'>%s</code></p>" +
            "</div>",
            relativePath
        );
        windowFactory.updateResult(cancelMessage, project);
    }

    private void handleComplete(Project project, String docFile) {
        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
        windowFactory.stopLoadingAnimation(project);

        String relativePath = new File(docFile).getAbsolutePath()
            .replace(project.getBasePath(), "")
            .replaceFirst("^[/\\\\]", "");

        String successMessage = String.format(
            "<div style='padding: 15px; background-color: #e8f5e8; border-left: 4px solid #4caf50; margin: 10px 0;'>" +
            "<h3 style='color: #2e7d32; margin-top: 0;'>✅ 类依赖关系图生成完成</h3>" +
            "<p style='margin: 5px 0;'><strong>📁 文档已保存至：</strong><code style='background: #f5f5f5; padding: 2px 4px; border-radius: 3px;'>%s</code></p>" +
            "<p style='margin: 5px 0;'><strong>💡 提示：</strong>流程图包含了该类的完整上下游依赖关系分析</p>" +
            "</div>",
            relativePath
        );
        windowFactory.updateResult(successMessage, project);
    }

    private void showMessage(Project project, String message) {
        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
        windowFactory.updateResult(message, project);
    }

    private void stopLoadingAndShowMessage(Project project, String message) {
        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
        windowFactory.stopLoadingAnimation(project);
        windowFactory.updateResult(message, project);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabled(file != null && file.getName().endsWith(".java"));
    }
} 