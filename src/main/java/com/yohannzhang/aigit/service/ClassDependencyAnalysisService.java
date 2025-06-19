package com.yohannzhang.aigit.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.yohannzhang.aigit.core.llm.LLMEngine;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ClassDependencyAnalysisService {
    private final LLMEngine llmEngine;
    private final AtomicBoolean isCancelled;
    private final Set<String> processedClasses;
    private final Map<String, Set<String>> upstreamDependencies;
    private final Map<String, Set<String>> downstreamDependencies;
    private final Map<String, Set<String>> methodDependencies;
    private static final int MAX_DEPENDENCIES = 100; // 最大依赖数量限制
    private static final int BATCH_SIZE = 20; // 每批处理的依赖数量

    public ClassDependencyAnalysisService(LLMEngine llmEngine) {
        this.llmEngine = llmEngine;
        this.isCancelled = new AtomicBoolean(false);
        this.processedClasses = new HashSet<>();
        this.upstreamDependencies = new HashMap<>();
        this.downstreamDependencies = new HashMap<>();
        this.methodDependencies = new HashMap<>();
    }

    public void setCancelled(boolean cancelled) {
        isCancelled.set(cancelled);
    }

    public String analyzeClassDependencies(Project project, PsiClass targetClass) {
        // 重置状态
        processedClasses.clear();
        upstreamDependencies.clear();
        downstreamDependencies.clear();
        methodDependencies.clear();

        try {
            // 分析上游依赖
            analyzeUpstreamDependencies(project, targetClass);
            
            // 分析下游依赖
            analyzeDownstreamDependencies(project, targetClass);
            
            // 分析方法依赖
            analyzeMethodDependencies(targetClass);

            // 生成分析报告
            return generateAnalysisReport(targetClass);
        } catch (Exception e) {
            return generateErrorReport(targetClass, e);
        }
    }

    private void analyzeUpstreamDependencies(Project project, PsiClass targetClass) {
        if (isCancelled.get() || processedClasses.contains(targetClass.getQualifiedName())) {
            return;
        }

        // 检查是否超过最大依赖数量
        if (processedClasses.size() >= MAX_DEPENDENCIES) {
            return;
        }

        processedClasses.add(targetClass.getQualifiedName());
        Set<String> dependencies = new HashSet<>();

        // 分析继承关系
        PsiClass superClass = targetClass.getSuperClass();
        if (superClass != null) {
            dependencies.add(superClass.getQualifiedName());
            analyzeUpstreamDependencies(project, superClass);
        }

        // 分析接口实现
        for (PsiClass interfaceClass : targetClass.getInterfaces()) {
            if (dependencies.size() >= BATCH_SIZE) break;
            dependencies.add(interfaceClass.getQualifiedName());
            analyzeUpstreamDependencies(project, interfaceClass);
        }

        // 分析字段类型
        for (PsiField field : targetClass.getAllFields()) {
            if (dependencies.size() >= BATCH_SIZE) break;
            PsiType fieldType = field.getType();
            if (fieldType instanceof PsiClassType) {
                PsiClass fieldClass = ((PsiClassType) fieldType).resolve();
                if (fieldClass != null) {
                    dependencies.add(fieldClass.getQualifiedName());
                    analyzeUpstreamDependencies(project, fieldClass);
                }
            }
        }

        // 分析方法参数和返回类型
        for (PsiMethod method : targetClass.getAllMethods()) {
            if (dependencies.size() >= BATCH_SIZE) break;
            // 分析参数类型
            for (PsiParameter parameter : method.getParameterList().getParameters()) {
                if (dependencies.size() >= BATCH_SIZE) break;
                PsiType paramType = parameter.getType();
                if (paramType instanceof PsiClassType) {
                    PsiClass paramClass = ((PsiClassType) paramType).resolve();
                    if (paramClass != null) {
                        dependencies.add(paramClass.getQualifiedName());
                        analyzeUpstreamDependencies(project, paramClass);
                    }
                }
            }

            // 分析返回类型
            PsiType returnType = method.getReturnType();
            if (returnType instanceof PsiClassType) {
                PsiClass returnClass = ((PsiClassType) returnType).resolve();
                if (returnClass != null) {
                    dependencies.add(returnClass.getQualifiedName());
                    analyzeUpstreamDependencies(project, returnClass);
                }
            }
        }

        upstreamDependencies.put(targetClass.getQualifiedName(), dependencies);
    }

    private void analyzeDownstreamDependencies(Project project, PsiClass targetClass) {
        if (isCancelled.get()) {
            return;
        }

        Set<String> dependents = new HashSet<>();
        String targetClassName = targetClass.getQualifiedName();
        AtomicInteger count = new AtomicInteger(0);

        // 查找所有引用
        ReferencesSearch.search(targetClass, GlobalSearchScope.projectScope(project))
            .forEach(reference -> {
                if (count.get() >= BATCH_SIZE) return;
                PsiElement element = reference.getElement();
                PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
                if (containingClass != null) {
                    dependents.add(containingClass.getQualifiedName());
                    count.incrementAndGet();
                }
            });

        downstreamDependencies.put(targetClassName, dependents);
    }

    private void analyzeMethodDependencies(PsiClass targetClass) {
        if (isCancelled.get()) {
            return;
        }

        String className = targetClass.getQualifiedName();
        Set<String> methods = new HashSet<>();

        // 收集所有方法
        for (PsiMethod method : targetClass.getAllMethods()) {
            methods.add(method.getName());
        }

        methodDependencies.put(className, methods);
    }

    private String generateAnalysisReport(PsiClass targetClass) {
        StringBuilder report = new StringBuilder();
        String className = targetClass.getQualifiedName();

        // 添加类的基本信息
        report.append("# 类依赖分析报告\n\n");
        report.append("## 目标类\n");
        report.append("- 类名: ").append(className).append("\n");
        PsiFile containingFile = targetClass.getContainingFile();
        if (containingFile instanceof PsiJavaFile) {
            report.append("- 包名: ").append(((PsiJavaFile) containingFile).getPackageName()).append("\n\n");
        } else {
            report.append("- 包名: 未知\n\n");
        }

        // 添加上游依赖
        report.append("## 上游依赖\n");
        Set<String> upstream = upstreamDependencies.getOrDefault(className, Collections.emptySet());
        if (upstream.isEmpty()) {
            report.append("无上游依赖\n");
        } else {
            for (String dependency : upstream) {
                report.append("- ").append(dependency).append("\n");
            }
        }
        report.append("\n");

        // 添加下游依赖
        report.append("## 下游依赖\n");
        Set<String> downstream = downstreamDependencies.getOrDefault(className, Collections.emptySet());
        if (downstream.isEmpty()) {
            report.append("无下游依赖\n");
        } else {
            for (String dependent : downstream) {
                report.append("- ").append(dependent).append("\n");
            }
        }
        report.append("\n");

        // 添加方法依赖
        report.append("## 方法列表\n");
        Set<String> methods = methodDependencies.getOrDefault(className, Collections.emptySet());
        if (methods.isEmpty()) {
            report.append("无方法\n");
        } else {
            for (String method : methods) {
                report.append("- ").append(method).append("\n");
            }
        }

        return report.toString();
    }

    private String generateErrorReport(PsiClass targetClass, Exception e) {
        StringBuilder report = new StringBuilder();
        String className = targetClass.getQualifiedName();

        report.append("# 类依赖分析报告\n\n");
        report.append("## 分析状态\n");
        report.append("⚠️ 分析过程中出现异常，可能是由于项目过大或依赖关系过于复杂。\n\n");
        report.append("## 目标类\n");
        report.append("- 类名: ").append(className).append("\n");
        PsiFile containingFile = targetClass.getContainingFile();
        if (containingFile instanceof PsiJavaFile) {
            report.append("- 包名: ").append(((PsiJavaFile) containingFile).getPackageName()).append("\n\n");
        } else {
            report.append("- 包名: 未知\n\n");
        }

        report.append("## 错误信息\n");
        report.append("```\n");
        report.append(e.getMessage()).append("\n");
        report.append("```\n\n");

        report.append("## 建议\n");
        report.append("1. 尝试分析更小的类或模块\n");
        report.append("2. 检查类的依赖关系是否过于复杂\n");
        report.append("3. 考虑重构代码以减少依赖\n");

        return report.toString();
    }

    public void generateDependencyFlowChart(String analysisResult, LLMEngine.StreamCallback callback) {
        if (isCancelled.get()) {
            callback.onError(new InterruptedException("Analysis cancelled"));
            return;
        }

        // 构建提示词
        String prompt = "请根据以下类依赖分析结果，生成一段清晰的中文伪代码，描述类的调用流程。要求：\n" +
                       "1. 使用自然的中文描述，避免使用编程语言的具体语法\n" +
                       "2. 按照调用顺序描述流程，包括：\n" +
                       "   - 类的初始化过程\n" +
                       "   - 主要方法的调用顺序\n" +
                       "   - 方法之间的依赖关系\n" +
                       "   - 重要的业务逻辑说明\n" +
                       "3. 使用缩进和分段来体现代码的层次结构\n" +
                       "4. 对于复杂的逻辑，添加注释说明\n" +
                       "5. 突出显示关键的业务流程和重要的判断条件\n" +
                       "6. 说明异常情况的处理方式\n" +
                       "7. 如果依赖关系过于复杂，只展示最重要的部分\n" +
                       "8. 使用简洁的语言，避免冗长的描述\n\n" +
                       "分析结果：\n" + analysisResult;

        // 使用LLM生成伪代码
        llmEngine.generateText(prompt, callback);
    }
} 