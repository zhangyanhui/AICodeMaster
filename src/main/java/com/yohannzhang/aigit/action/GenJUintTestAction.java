package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.core.analysis.BaseCodeAnalyzer;
import com.yohannzhang.aigit.core.llm.LLMEngine;
import com.yohannzhang.aigit.core.llm.LLMEngineFactory;
import com.yohannzhang.aigit.service.AnalysisService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import com.yohannzhang.aigit.util.OpenAIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenJUintTestAction extends AnAction {
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("No project found", "Error");
            return;
        }

        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        if (editor == null) {
            Messages.showErrorDialog("No editor found", "Error");
            return;
        }

        // Get cursor position and validate file
        int offset = editor.getCaretModel().getOffset();
        PsiFile psiFile = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null || !(psiFile instanceof PsiJavaFile)) {
            Messages.showErrorDialog("Please place cursor in a Java file", "Error");
            return;
        }

        // Get target method and class
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null) {
            Messages.showErrorDialog("Cannot find element at cursor position", "Error");
            return;
        }

        PsiMethod targetMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (targetMethod == null) {
            Messages.showErrorDialog("Please place cursor inside a method", "Error");
            return;
        }

        PsiClass targetClass = PsiTreeUtil.getParentOfType(targetMethod, PsiClass.class);
        if (targetClass == null) {
            Messages.showErrorDialog("Cannot find containing class", "Error");
            return;
        }

        // Check API configuration
        ApiKeySettings settings = ApiKeySettings.getInstance();
        String selectedClient = settings.getSelectedClient();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(selectedClient);
        
        if (moduleConfig == null) {
            Messages.showErrorDialog("API configuration is missing. Please configure the API settings first.", "Error");
            return;
        }
        
        if (!OpenAIUtil.checkNecessaryModuleConfigIsRight(selectedClient)) {
            Messages.showErrorDialog("API configuration is incomplete. Please check your API settings.", "Error");
            return;
        }

        // Show tool window and start loading
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow != null) {
            toolWindow.show();
        }

        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
//        windowFactory.startLoadingAnimation(project);
        windowFactory.updateResult("正在准备生成测试用例...\n", project);

        // Build code context
        StringBuilder codeContext = new StringBuilder();
        codeContext.append("// Class: ").append(targetClass.getName()).append("\n");
        if (targetClass.getContainingFile() instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) targetClass.getContainingFile();
            codeContext.append("package ").append(javaFile.getPackageName()).append(";\n\n");
        }
        codeContext.append("public class ").append(targetClass.getName()).append(" {\n");
        codeContext.append("    // Method: ").append(targetMethod.getName()).append("\n");
        codeContext.append("    ").append(targetMethod.getText()).append("\n");
        codeContext.append("}\n");

        // Collect dependencies and build context
        List<PsiElement> dependencies = collectDependencies(targetClass, targetMethod);
        String context = buildContext(targetClass, targetMethod, dependencies);

        // Create service instances
        LLMEngine llmEngine = LLMEngineFactory.createEngine(selectedClient, moduleConfig);
        AnalysisService analysisService = new AnalysisService(new BaseCodeAnalyzer(), llmEngine);
        StringBuilder result = new StringBuilder();
        // Execute test generation asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                analysisService.generateTests(
                    codeContext.toString(),
                    "java",
                    context,
                    new LLMEngine.StreamCallback() {
                        @Override
                        public void onStart() {
                            windowFactory.submitButton(project);
                            windowFactory.updateResult("开始生成测试用例...\n", project);
                        }

                        @Override
                        public void onToken(String token) {
                            if (token != null && !token.isEmpty()) {
                                result.append(token);
                                windowFactory.updateResult(result.toString(), project);
                            }
                        }

                        @Override
                        public void onError(Throwable error) {
//                            windowFactory.stopLoadingAnimation(project);
                            String errorMessage = error.getMessage();
                            if (errorMessage == null || errorMessage.isEmpty()) {
                                errorMessage = "未知错误";
                            }
                            windowFactory.updateResult("\n生成测试失败: " + errorMessage, project);
                            windowFactory.resetButton(project);
                        }

                        @Override
                        public void onComplete() {
//                            windowFactory.stopLoadingAnimation(project);
                            windowFactory.updateResult(result.toString(), project);
//                            windowFactory.updateResult("\n测试生成完成", project);
                            windowFactory.resetButton(project);
                        }
                    });
            } catch (Exception ex) {
//                windowFactory.stopLoadingAnimation(project);
                String errorMessage = ex.getMessage();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = "未知错误";
                }
                windowFactory.updateResult("发生错误: " + errorMessage, project);
                windowFactory.resetButton(project);
            }
        });
    }

    private List<PsiElement> collectDependencies(PsiClass targetClass, PsiMethod targetMethod) {
        List<PsiElement> dependencies = new ArrayList<>();

        // Collect inner classes
        PsiClass[] innerClasses = targetClass.getInnerClasses();
        for (PsiClass innerClass : innerClasses) {
            dependencies.add(innerClass);
        }

        // Collect method references
        PsiReference[] methodRefs = targetMethod.getReferences();
        for (PsiReference ref : methodRefs) {
            PsiElement resolved = ref.resolve();
            if (resolved != null) {
                dependencies.add(resolved);
            }
        }

        // Collect fields
        PsiField[] fields = targetClass.getAllFields();
        for (PsiField field : fields) {
            dependencies.add(field);
        }

        // Collect superclass
        PsiClass superClass = targetClass.getSuperClass();
        if (superClass != null) {
            dependencies.add(superClass);
        }

        // Collect interfaces
        PsiClass[] interfaces = targetClass.getInterfaces();
        for (PsiClass iface : interfaces) {
            dependencies.add(iface);
        }

        return dependencies;
    }

    private String buildContext(PsiClass targetClass, PsiMethod targetMethod, List<PsiElement> dependencies) {
        StringBuilder context = new StringBuilder();
        
        // Add class information
        context.append("Target Class:\n");
        context.append("Name: ").append(targetClass.getName()).append("\n");
        if (targetClass.getContainingFile() instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) targetClass.getContainingFile();
            context.append("Package: ").append(javaFile.getPackageName()).append("\n");
        }
        context.append("\n");

        // Add method information
        context.append("Target Method:\n");
        context.append("Name: ").append(targetMethod.getName()).append("\n");
        context.append("Return Type: ").append(targetMethod.getReturnType().getPresentableText()).append("\n");
        context.append("Parameters:\n");
        for (PsiParameter param : targetMethod.getParameterList().getParameters()) {
            context.append("- ").append(param.getType().getPresentableText())
                   .append(" ").append(param.getName()).append("\n");
        }
        context.append("\n");

        // Add dependencies
        context.append("Dependencies:\n");
        for (PsiElement dep : dependencies) {
            context.append(dep.getText()).append("\n\n");
        }

        // Add test generation requirements
        context.append("\n测试生成要求：\n");
        context.append("1. 使用JUnit 5进行测试\n");
        context.append("2. 包含覆盖所有代码路径的必要测试用例\n");
        context.append("3. 使用Mockito模拟依赖项\n");
        context.append("4. 添加合适的测试注解\n");
        context.append("5. 遵循测试最佳实践\n");
        context.append("6. 包含正向和负向测试用例\n");
        context.append("7. 测试边界条件和极端情况\n");
        context.append("8. 命名清晰的测试方法并添加注释\n");

        return context.toString();
    }
}