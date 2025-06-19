package com.yohannzhang.aigit.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.ui.Messages;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.factory.AIServiceFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class RagService {
    private static final String RAG_RESULTS_DIR = "rag_results";
    private static final String DOCS_DIR = "docs";
    private final Project project;

    public RagService(Project project) {
        this.project = project;
    }

    public String performRag() {
        String projectPath = project.getBasePath();
        Path ragDir = Paths.get(projectPath, RAG_RESULTS_DIR);
        Path docsDir = Paths.get(projectPath, DOCS_DIR);

        try {
            Files.createDirectories(ragDir);
        } catch (IOException e) {
            Messages.showErrorDialog(project, "Failed to create directories: " + e.getMessage(), "Error");
            return projectPath;
        }

        Map<String, Map<String, List<String>>> moduleCodeInfo = new HashMap<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(projectPath);
        if (projectDir != null) {
            collectCodeInfo(psiManager.findDirectory(projectDir), moduleCodeInfo);
        }

        saveRagResults(moduleCodeInfo, ragDir);

        // Generate documentation using LLM
//        generateDocumentationWithLLM(moduleCodeInfo, docsDir);
        return projectPath;
    }

    private void collectCodeInfo(PsiDirectory directory, Map<String, Map<String, List<String>>> moduleCodeInfo) {
        if (directory == null) return;

        directory.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitFile(PsiFile file) {
                if (file instanceof PsiJavaFile) {
                    PsiJavaFile javaFile = (PsiJavaFile) file;
                    String fileName = javaFile.getName();
                    String packageName = javaFile.getPackageName();
                    String moduleType = determineModuleType(packageName);

                    // Initialize module map and file info list
                    moduleCodeInfo.computeIfAbsent(moduleType, k -> new HashMap<>());
                    List<String> fileInfo = new ArrayList<>();

                    for (PsiClass psiClass : javaFile.getClasses()) {
                        fileInfo.add("Class: " + psiClass.getName());

                        for (PsiField field : psiClass.getAllFields()) {
                            fileInfo.add("  Field: " + field.getName() + " - " + field.getType().getPresentableText());
                        }

                        for (PsiMethod method : psiClass.getAllMethods()) {
                            String returnType = method.getReturnType() != null ?
                                    method.getReturnType().getPresentableText() : "void";
                            fileInfo.add("  Method: " + method.getName() + " - " + returnType);
                        }
                    }

                    // Add file info to the corresponding module
                    moduleCodeInfo.get(moduleType).put(fileName, fileInfo);
                }
            }
        });
    }

    private String determineModuleType(String packageName) {
        if (packageName.contains("controller")) {
            return "Controller Layer";
        } else if (packageName.contains("service")) {
            return "Service Layer";
        } else if (packageName.contains("repository") || packageName.contains("dao")) {
            return "Repository Layer";
        }
        return "Other";
    }

    private void saveRagResults(Map<String, Map<String, List<String>>> moduleCodeInfo, Path ragDir) {
        try {
            for (Map.Entry<String, Map<String, List<String>>> moduleEntry : moduleCodeInfo.entrySet()) {
                String moduleType = moduleEntry.getKey();
                Map<String, List<String>> codeInfo = moduleEntry.getValue();

                Path moduleDir = ragDir.resolve(moduleType.replace(" ", "_").toLowerCase());
                Files.createDirectories(moduleDir);

                // Save as text file
                Path textFile = moduleDir.resolve("code_analysis.txt");
                List<String> lines = new ArrayList<>();
                for (Map.Entry<String, List<String>> entry : codeInfo.entrySet()) {
                    lines.add("File: " + entry.getKey());
                    lines.addAll(entry.getValue());
                    lines.add("\n" + "-".repeat(80) + "\n");
                }
                Files.write(textFile, lines);

                // Save as JSON file
                Path jsonFile = moduleDir.resolve("code_analysis.json");
                String json = codeInfo.entrySet().stream()
                        .map(entry -> String.format("\"%s\": %s",
                                entry.getKey(),
                                entry.getValue().stream()
                                        .map(line -> "\"" + line.replace("\"", "\\\"") + "\"")
                                        .collect(Collectors.joining(",", "[", "]"))))
                        .collect(Collectors.joining(",", "{", "}"));
                Files.write(jsonFile, json.getBytes());
            }
        } catch (IOException e) {
            Messages.showErrorDialog(project, "Failed to save RAG results: " + e.getMessage(), "Error");
        }
    }
}

