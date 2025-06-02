package com.yohannzhang.aigit.service;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.yohannzhang.aigit.core.llm.LLMEngine;
import com.yohannzhang.aigit.core.llm.LLMEngine.StreamCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CodeAssistService {
    private final LLMEngine llmEngine;
    private final Project project;

    public CodeAssistService(LLMEngine llmEngine, Project project) {
        this.llmEngine = llmEngine;
        this.project = project;
    }

    private String getProjectContext(PsiFile psiFile) {
        StringBuilder contextBuilder = new StringBuilder();
        
        // 1. 获取项目基本信息
        contextBuilder.append("项目信息：\n");
        contextBuilder.append("- 项目名称：").append(project.getName()).append("\n");
        contextBuilder.append("- 项目路径：").append(project.getBasePath()).append("\n");
        
        // 2. 获取当前文件信息
        if (psiFile != null) {
            contextBuilder.append("\n当前文件信息：\n");
            contextBuilder.append("- 文件名：").append(psiFile.getName()).append("\n");
            contextBuilder.append("- 文件路径：").append(psiFile.getVirtualFile().getPath()).append("\n");
            
            // 3. 获取当前类的信息
            PsiClass containingClass = PsiTreeUtil.getParentOfType(psiFile.findElementAt(0), PsiClass.class);
            if (containingClass != null) {
                contextBuilder.append("\n当前类信息：\n");
                contextBuilder.append("- 类名：").append(containingClass.getName()).append("\n");
                
                // 获取包名
                PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(containingClass.getQualifiedName());
                if (psiPackage != null) {
                    contextBuilder.append("- 包名：").append(psiPackage.getQualifiedName()).append("\n");
                }
                
                // 获取类的继承关系
                PsiClass superClass = containingClass.getSuperClass();
                if (superClass != null) {
                    contextBuilder.append("- 父类：").append(superClass.getQualifiedName()).append("\n");
                }
                
                // 获取实现的接口
                PsiClass[] interfaces = containingClass.getInterfaces();
                if (interfaces.length > 0) {
                    contextBuilder.append("- 实现的接口：\n");
                    for (PsiClass iface : interfaces) {
                        contextBuilder.append("  * ").append(iface.getQualifiedName()).append("\n");
                    }
                }
                
                // 获取类的方法
                PsiMethod[] methods = containingClass.getMethods();
                if (methods.length > 0) {
                    contextBuilder.append("\n类的方法：\n");
                    for (PsiMethod method : methods) {
                        contextBuilder.append("- ").append(method.getName())
                            .append("(").append(getMethodParameters(method)).append(")\n");
                    }
                }
            }
        }
        
        return contextBuilder.toString();
    }

    private String getMethodParameters(PsiMethod method) {
        return method.getParameterList().getParameters().length > 0 ?
            method.getParameterList().getParameters().length + " 个参数" : "无参数";
    }

    private String getSurroundingContext(Document document, int offset, int linesBefore, int linesAfter) {
        int lineNumber = document.getLineNumber(offset);
        int startLine = Math.max(0, lineNumber - linesBefore);
        int endLine = Math.min(document.getLineCount() - 1, lineNumber + linesAfter);
        
        StringBuilder context = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            int lineStart = document.getLineStartOffset(i);
            int lineEnd = document.getLineEndOffset(i);
            String line = document.getText().substring(lineStart, lineEnd);
            if (i == lineNumber) {
                context.append("> ").append(line).append("\n");
            } else {
                context.append("  ").append(line).append("\n");
            }
        }
        return context.toString();
    }

    public void getCodeCompletion(Editor editor, StreamCallback callback) {
        try {
            Document document = editor.getDocument();
            int caretOffset = editor.getCaretModel().getOffset();
            PsiFile psiFile = com.intellij.psi.PsiDocumentManager.getInstance(project)
                .getPsiFile(document);

            // 获取项目上下文
            String projectContext = getProjectContext(psiFile);
            
            // 获取当前行和周围代码的上下文
            String codeContext = getSurroundingContext(document, caretOffset, 5, 5);

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("请根据以下项目上下文和代码，提供智能代码补全建议：\n\n");
            promptBuilder.append(projectContext).append("\n");
            promptBuilder.append("当前代码上下文：\n").append(codeContext).append("\n");
            promptBuilder.append("请提供3-5个可能的补全建议，每个建议包含：\n");
            promptBuilder.append("1. 补全内容\n");
            promptBuilder.append("2. 简短说明\n");
            promptBuilder.append("3. 适用场景\n");
            promptBuilder.append("4. 与当前代码的关联性\n");

            llmEngine.generateCode(promptBuilder.toString(), codeContext, "java", callback);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public void getCodeSnippets(Editor editor, StreamCallback callback) {
        try {
            Document document = editor.getDocument();
            int caretOffset = editor.getCaretModel().getOffset();
            PsiFile psiFile = com.intellij.psi.PsiDocumentManager.getInstance(project)
                .getPsiFile(document);
            
            // 获取项目上下文
            String projectContext = getProjectContext(psiFile);
            
            // 获取选中的代码块
            int selectionStart = editor.getSelectionModel().getSelectionStart();
            int selectionEnd = editor.getSelectionModel().getSelectionEnd();
            String selectedCode = selectionStart < selectionEnd ? 
                document.getText().substring(selectionStart, selectionEnd) : "";
            
            // 获取周围代码的上下文
            String codeContext = getSurroundingContext(document, caretOffset, 5, 5);

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("请根据以下项目上下文和代码，推荐相关的代码片段：\n\n");
            promptBuilder.append(projectContext).append("\n");
            if (!selectedCode.isEmpty()) {
                promptBuilder.append("选中的代码：\n").append(selectedCode).append("\n\n");
            }
            promptBuilder.append("当前代码上下文：\n").append(codeContext).append("\n");
            promptBuilder.append("请提供3-5个相关的代码片段，每个片段包含：\n");
            promptBuilder.append("1. 代码片段\n");
            promptBuilder.append("2. 使用说明\n");
            promptBuilder.append("3. 最佳实践\n");
            promptBuilder.append("4. 与当前代码的集成建议\n");

            llmEngine.generateCode(promptBuilder.toString(), codeContext, "java", callback);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public void getContextualExamples(Editor editor, StreamCallback callback) {
        try {
            Document document = editor.getDocument();
            int caretOffset = editor.getCaretModel().getOffset();
            PsiFile psiFile = com.intellij.psi.PsiDocumentManager.getInstance(project)
                .getPsiFile(document);
            
            // 获取项目上下文
            String projectContext = getProjectContext(psiFile);
            
            // 获取当前行的上下文
            String codeContext = getSurroundingContext(document, caretOffset, 10, 10);

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("请根据以下项目上下文和代码，提供相关的代码示例：\n\n");
            promptBuilder.append(projectContext).append("\n");
            promptBuilder.append("当前代码上下文：\n").append(codeContext).append("\n");
            promptBuilder.append("请提供2-3个相关的代码示例，每个示例包含：\n");
            promptBuilder.append("1. 示例代码\n");
            promptBuilder.append("2. 实现说明\n");
            promptBuilder.append("3. 使用场景\n");
            promptBuilder.append("4. 与当前项目的集成建议\n");
            promptBuilder.append("5. 性能考虑\n");

            llmEngine.generateCode(promptBuilder.toString(), codeContext, "java", callback);
        } catch (Exception e) {
            callback.onError(e);
        }
    }
} 