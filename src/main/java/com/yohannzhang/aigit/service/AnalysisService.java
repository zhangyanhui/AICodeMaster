package com.yohannzhang.aigit.service;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.yohannzhang.aigit.core.analysis.BaseCodeAnalyzer;
import com.yohannzhang.aigit.core.llm.LLMEngine;
import com.yohannzhang.aigit.core.llm.LLMEngine.StreamCallback;
import com.yohannzhang.aigit.util.OpenAIUtil;
import com.yohannzhang.aigit.service.impl.OllamaService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;
import java.util.HashMap;
import java.util.ArrayList;

public class AnalysisService {
    private final BaseCodeAnalyzer codeAnalyzer;
    private final LLMEngine llmEngine;
    private final AtomicBoolean isCancelled;

    public AnalysisService(BaseCodeAnalyzer codeAnalyzer, LLMEngine llmEngine) {
        this.codeAnalyzer = codeAnalyzer;
        this.llmEngine = llmEngine;
        this.isCancelled = new AtomicBoolean(false);
    }

    public void setCancelled(boolean cancelled) {
        isCancelled.set(cancelled);
    }

    public Map<String, Object> analyzeCode(String code, String language) {
        return llmEngine.analyzeCode(code, language);
    }

    public void optimizeCode(String code, String language, List<String> suggestions, StreamCallback callback) {
        llmEngine.optimizeCode(code, language, suggestions, callback);
    }

    public void generateTests(String code, String language, String context, StreamCallback callback) {
        llmEngine.generateTests(code, language, context, callback);
    }

    public String generateDocumentation(String code, String language) {
        return llmEngine.generateDocumentation(code, language);
    }

    public void generateProjectDocumentation(String prompt, StreamCallback callback) {
        try {
            if (isCancelled.get()) {
                throw new ProcessCanceledException();
            }
            
            callback.onStart();
            


            // 使用 LLM 引擎生成文档
            llmEngine.generateText(prompt, new StreamCallback() {
                @Override
                public void onStart() {
                    if (isCancelled.get()) {
                        throw new ProcessCanceledException();
                    }
                    callback.onStart();
                }

                @Override
                public void onToken(String token) {
                    if (isCancelled.get()) {
                        // 立即取消 LLM 请求
                        OpenAIUtil.cancelRequest();
                        OllamaService.cancelRequest();
                        throw new ProcessCanceledException();
                    }
                    callback.onToken(token);
                }

                @Override
                public void onError(Throwable error) {
                    if (error instanceof ProcessCanceledException || isCancelled.get()) {
                        // 确保 LLM 请求被取消
                        OpenAIUtil.cancelRequest();
                        OllamaService.cancelRequest();
                        callback.onError(new ProcessCanceledException());
                    } else {
                        callback.onError(error);
                    }
                }

                @Override
                public void onComplete() {
                    if (!isCancelled.get()) {
                        callback.onComplete();
                    } else {
                        callback.onError(new ProcessCanceledException());
                    }
                }
            });
        } catch (ProcessCanceledException e) {
            // 确保 LLM 请求被取消
            OpenAIUtil.cancelRequest();
            OllamaService.cancelRequest();
            callback.onError(e);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public String generateApiDocumentation(List<String> files) {
        // 首先分析文件结构，生成文件概览
        StringBuilder fileOverview = new StringBuilder();
        fileOverview.append("项目文件结构概览：\n");
        for (String file : files) {
            try {
                String fileName = new File(file).getName();
                String fileContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)));
                // 只读取文件的前几行来了解文件结构
                String[] lines = fileContent.split("\n");
                int previewLines = Math.min(10, lines.length);
                fileOverview.append("\n文件：").append(fileName).append("\n");
                fileOverview.append("预览内容：\n");
                for (int i = 0; i < previewLines; i++) {
                    fileOverview.append(lines[i]).append("\n");
                }
                fileOverview.append("...\n");
            } catch (Exception e) {
                fileOverview.append("无法读取文件：").append(file).append("\n");
            }
        }

        // 构建初始提示
        String initialPrompt = "请分析以下项目文件结构，并生成详细的 API 文档。\n" +
                "文档应包括：\n" +
                "1. 每个 API 的完整路径\n" +
                "2. 请求方法（GET、POST 等）\n" +
                "3. 请求参数说明\n" +
                "4. 响应格式和示例\n" +
                "5. 错误码说明\n" +
                "6. 使用示例\n\n" +
                fileOverview.toString();

        return initialPrompt;
    }

    public String generateUmlDiagram(List<String> files) {
        // 分析代码结构，提取类、接口、关系等信息
        StringBuilder structureInfo = new StringBuilder();
        structureInfo.append("项目结构分析：\n\n");

        // 用于存储类信息的 Map
        Map<String, ClassInfo> classMap = new HashMap<>();
        
        for (String file : files) {
            try {
                String fileName = new File(file).getName();
                if (!fileName.endsWith(".java")) continue;  // 只处理 Java 文件
                
                String fileContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)));
                
                // 提取包名
                String packageName = extractPackageName(fileContent);
                
                // 提取类名
                String className = extractClassName(fileName);
                
                // 提取类的修饰符（public, abstract, interface 等）
                String modifiers = extractModifiers(fileContent);
                
                // 提取继承关系
                String extendsClass = extractExtends(fileContent);
                
                // 提取实现的接口
                List<String> implementsList = extractImplements(fileContent);
                
                // 提取字段
                List<String> fields = extractFields(fileContent);
                
                // 提取方法
                List<String> methods = extractMethods(fileContent);
                
                // 存储类信息
                ClassInfo classInfo = new ClassInfo(className, packageName, modifiers, extendsClass, implementsList, fields, methods);
                classMap.put(className, classInfo);
                
                // 添加到结构信息
                structureInfo.append("类：").append(className).append("\n");
                structureInfo.append("包：").append(packageName).append("\n");
                structureInfo.append("修饰符：").append(modifiers).append("\n");
                if (extendsClass != null) {
                    structureInfo.append("继承：").append(extendsClass).append("\n");
                }
                if (!implementsList.isEmpty()) {
                    structureInfo.append("实现接口：").append(String.join(", ", implementsList)).append("\n");
                }
                structureInfo.append("字段：\n");
                for (String field : fields) {
                    structureInfo.append("  - ").append(field).append("\n");
                }
                structureInfo.append("方法：\n");
                for (String method : methods) {
                    structureInfo.append("  - ").append(method).append("\n");
                }
                structureInfo.append("\n");
                
            } catch (Exception e) {
                structureInfo.append("无法分析文件：").append(file).append("\n");
            }
        }

        // 构建 UML 图生成提示
        String prompt = "请根据以下项目结构分析生成 PlantUML 格式的 UML 类图。\n" +
                "要求：\n" +
                "1. 使用 PlantUML 语法\n" +
                "2. 包含所有类的继承关系\n" +
                "3. 包含所有接口实现关系\n" +
                "4. 包含重要的字段和方法\n" +
                "5. 使用合适的布局和样式\n" +
                "6. 添加适当的注释说明\n\n" +
                "项目结构分析：\n" + structureInfo.toString();

        return prompt;
    }

    private String extractPackageName(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("package\\s+([\\w.]+);");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractClassName(String fileName) {
        return fileName.substring(0, fileName.length() - 5);  // 移除 .java 后缀
    }

    private String extractModifiers(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(public|private|protected)?\\s*(abstract|final)?\\s*(class|interface)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return "";
    }

    private String extractExtends(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("extends\\s+(\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private List<String> extractImplements(String content) {
        List<String> interfaces = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("implements\\s+([\\w,\\s]+)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String[] interfaceNames = matcher.group(1).split(",");
            for (String name : interfaceNames) {
                interfaces.add(name.trim());
            }
        }
        return interfaces;
    }

    private List<String> extractFields(String content) {
        List<String> fields = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(private|protected|public)\\s+([\\w<>]+)\\s+(\\w+)\\s*;");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            fields.add(matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3));
        }
        return fields;
    }

    private List<String> extractMethods(String content) {
        List<String> methods = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(private|protected|public)\\s+([\\w<>]+)\\s+(\\w+)\\s*\\([^)]*\\)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            methods.add(matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3) + "()");
        }
        return methods;
    }

    private static class ClassInfo {
        String className;
        String packageName;
        String modifiers;
        String extendsClass;
        List<String> implementsList;
        List<String> fields;
        List<String> methods;

        public ClassInfo(String className, String packageName, String modifiers, String extendsClass, 
                        List<String> implementsList, List<String> fields, List<String> methods) {
            this.className = className;
            this.packageName = packageName;
            this.modifiers = modifiers;
            this.extendsClass = extendsClass;
            this.implementsList = implementsList;
            this.fields = fields;
            this.methods = methods;
        }
    }

    public String generateDependencyGraph(List<String> files) {
        String prompt = "请为以下代码生成依赖关系图，使用 Mermaid 语法。包括：\n" +
                "1. 模块之间的依赖关系\n" +
                "2. 类之间的依赖关系\n" +
                "3. 包之间的依赖关系\n" +
                "4. 外部依赖\n" +
                "5. 循环依赖（如果有）\n\n" +
                "代码内容：\n" + readFiles(files);
        return prompt;
//        llmEngine.generateText(prompt, callback);
    }

    public void generateBilingualDocumentation(List<String> files, LLMEngine.StreamCallback callback) {
        String prompt = "请为以下代码生成中英双语文档，包括：\n" +
                "1. 项目概述（中英文）\n" +
                "2. 架构设计（中英文）\n" +
                "3. 核心类说明（中英文）\n" +
                "4. 关键方法说明（中英文）\n" +
                "5. 配置说明（中英文）\n" +
                "6. 部署说明（中英文）\n\n" +
                "代码内容：\n" + readFiles(files);
        llmEngine.generateText(prompt, callback);
    }

    public String readFiles(List<String> files) {
        StringBuilder content = new StringBuilder();
        for (String file : files) {
            if (isCancelled.get()) {
                throw new ProcessCanceledException();
            }
            try {
                String fileContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)));
                content.append("文件：").append(file).append("\n");
                content.append(fileContent).append("\n\n");
            } catch (Exception e) {
                content.append("无法读取文件：").append(file).append("\n");
            }
        }
        return content.toString();
    }
} 