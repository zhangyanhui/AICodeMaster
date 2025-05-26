package com.yohannzhang.aigit.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.yohannzhang.aigit.util.OpenAIUtil;
import com.yohannzhang.aigit.config.ApiKeySettings;

public class TestClassGeneratorService {
    private final Project project;
    private final PsiClass sourceClass;

    public TestClassGeneratorService(Project project, PsiClass sourceClass) {
        this.project = project;
        this.sourceClass = sourceClass;
    }

    public void generateTestClass(TestGenerationCallback callback) {
        // 构建提示词
        String prompt = buildPrompt();
        
        // 获取当前选择的模型
        String selectedModel = ApiKeySettings.getInstance().getSelectedModule();
        
        try {
            // 调用 AI 生成测试类
            OpenAIUtil.getAIResponseStream(
                selectedModel,
                prompt,
                token -> {
                    // 处理流式响应
                    callback.onTokenReceived(token);
                },
                error -> {
                    callback.onError(error);
                },
                () -> {
                    callback.onComplete();
                }
            );
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private String buildPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下Java类生成对应的单元测试类。要求：\n");
        prompt.append("1. 使用JUnit 5框架\n");
        prompt.append("2. 包含必要的测试用例\n");
        prompt.append("3. 使用Mockito进行模拟\n");
        prompt.append("4. 添加适当的测试注释\n");
        prompt.append("5. 遵循测试最佳实践\n\n");
        prompt.append("源类代码：\n");
        prompt.append(sourceClass.getText());
        
        return prompt.toString();
    }

    public interface TestGenerationCallback {
        void onTokenReceived(String token);
        void onError(Throwable error);
        void onComplete();
    }
} 