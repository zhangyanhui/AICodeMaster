package com.yohannzhang.aigit.core.analyzers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.yohannzhang.aigit.core.models.FileMetadata;
import com.yohannzhang.aigit.core.models.Symbol;
import com.yohannzhang.aigit.core.models.ProjectMetadata;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BaseCodeAnalyzer {
    private final Map<String, FileMetadata> fileCache = new ConcurrentHashMap<>();
    private final Set<String> processedFiles = Collections.synchronizedSet(new HashSet<>());
    private static final int MAX_CACHE_SIZE = 1000;
    private static final int BATCH_SIZE = 50;

    public ProjectMetadata analyzeProject(Path projectPath) {
        ProjectMetadata project = new ProjectMetadata(projectPath.toString());
        Map<String, FileMetadata> files = new ConcurrentHashMap<>();
        
        // 使用分批处理来减少内存使用
        List<java.io.File> sourceFiles = Arrays.stream(projectPath.toFile().listFiles())
            .filter(file -> file.isFile() && isSourceFile(file.getName()))
            .collect(Collectors.toList());

        // 分批处理文件
        for (int i = 0; i < sourceFiles.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, sourceFiles.size());
            List<java.io.File> batch = sourceFiles.subList(i, end);
            
            // 并行处理当前批次
            batch.parallelStream().forEach(file -> {
                String filePath = file.getPath();
                if (!processedFiles.contains(filePath)) {
                    FileMetadata metadata = analyzeFileLightweight(file);
                    if (metadata != null) {
                        files.put(filePath, metadata);
                        processedFiles.add(filePath);
                        
                        // 管理缓存大小
                        if (fileCache.size() > MAX_CACHE_SIZE) {
                            fileCache.clear();
                        }
                    }
                }
            });
        }

        // 计算项目级别的指标
        calculateProjectMetrics(project, files);
        
        project.setFiles(files);
        return project;
    }

    private void calculateProjectMetrics(ProjectMetadata project, Map<String, FileMetadata> files) {
        AtomicInteger totalLines = new AtomicInteger(0);
        AtomicInteger totalCommentLines = new AtomicInteger(0);
        AtomicInteger totalComplexity = new AtomicInteger(0);
        Map<String, Integer> languageStats = new ConcurrentHashMap<>();
        
        files.values().parallelStream().forEach(metadata -> {
            totalLines.addAndGet(metadata.getLines());
            totalCommentLines.addAndGet(metadata.getCommentLines());
            totalComplexity.addAndGet(metadata.getComplexity());
            languageStats.merge(metadata.getLanguage(), 1, Integer::sum);
        });

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalLines", totalLines.get());
        metrics.put("totalCommentLines", totalCommentLines.get());
        metrics.put("totalComplexity", totalComplexity.get());
        metrics.put("languageStats", languageStats);
        metrics.put("fileCount", files.size());
        metrics.put("commentRatio", totalLines.get() > 0 ? 
            (double) totalCommentLines.get() / totalLines.get() : 0);
        metrics.put("avgComplexity", files.size() > 0 ? 
            (double) totalComplexity.get() / files.size() : 0);

        project.setMetrics(metrics);
    }

    private FileMetadata analyzeFileLightweight(java.io.File file) {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            String[] lines = content.split("\n");
            
            FileMetadata metadata = new FileMetadata(
                file.getPath(),
                getFileLanguage(file.getName()),
                file.length(),
                lines.length,
                LocalDateTime.ofInstant(java.nio.file.Files.getLastModifiedTime(file.toPath()).toInstant(), 
                    java.util.TimeZone.getDefault().toZoneId()),
                calculateContentHash(content),
                new HashMap<>(),
                new ArrayList<>(),
                ""
            );

            // 轻量级分析
            metadata.setCommentLines(countCommentLinesLightweight(content));
            metadata.setComplexity(calculateComplexityLightweight(content));
            
            // 缓存结果
            fileCache.put(file.getPath(), metadata);
            return metadata;
        } catch (Exception e) {
            return null;
        }
    }

    private int countCommentLinesLightweight(String content) {
        int commentLines = 0;
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || 
                trimmed.startsWith("/*") || 
                trimmed.startsWith("*") || 
                trimmed.startsWith("#")) {
                commentLines++;
            }
        }
        return commentLines;
    }

    private int calculateComplexityLightweight(String content) {
        int complexity = 1; // 基础复杂度
        String[] patterns = {"if", "for", "while", "switch", "case", "catch", "&&", "||"};
        for (String pattern : patterns) {
            complexity += countOccurrences(content, pattern);
        }
        return complexity;
    }

    private int countOccurrences(String content, String pattern) {
        return (content.length() - content.replace(pattern, "").length()) / pattern.length();
    }

    private String calculateContentHash(String content) {
        return String.valueOf(content.hashCode());
    }

    private boolean isSourceFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".java") || 
               lowerName.endsWith(".kt") || 
               lowerName.endsWith(".groovy") ||
               lowerName.endsWith(".scala");
    }

    private String getFileLanguage(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".java")) return "Java";
        if (lowerName.endsWith(".kt")) return "Kotlin";
        if (lowerName.endsWith(".groovy")) return "Groovy";
        if (lowerName.endsWith(".scala")) return "Scala";
        return "Unknown";
    }

//    public FileMetadata analyzeFile(PsiJavaFile file) {
//        FileMetadata metadata = new FileMetadata();
//        metadata.setFileName(file.getName());
//        metadata.setTotalLines(countLines(file));
//        metadata.setCommentLines(countCommentLines(file));
//        metadata.setComplexity(calculateComplexity(file));
//
//        // 收集方法和类
//        file.accept(new JavaRecursiveElementVisitor() {
//            @Override
//            public void visitMethod(PsiMethod method) {
//                Symbol methodSymbol = new Symbol(
//                    method.getName(),
//                    method.getReturnType().getPresentableText(),
//                    method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC) ? "public" :
//                    method.getModifierList().hasModifierProperty(PsiModifier.PROTECTED) ? "protected" :
//                    method.getModifierList().hasModifierProperty(PsiModifier.PRIVATE) ? "private" : "package-private",
//                    method.getTextRange().getStartOffset(),
//                    method.getTextRange().getEndOffset(),
//                    getModifiers(method.getModifierList()),
//                    new HashMap<>(),
//                    method.getDocComment() != null ? method.getDocComment().getText() : "",
//                    new ArrayList<>()
//                );
//                methodSymbol.setContent(method.getText());
//                metadata.getMethods().add(methodSymbol);
//                super.visitMethod(method);
//            }
//
//            @Override
//            public void visitClass(PsiClass clazz) {
//                Symbol classSymbol = new Symbol(
//                    clazz.getName(),
//                    clazz.isInterface() ? "interface" : "class",
//                    clazz.getModifierList().hasModifierProperty(PsiModifier.PUBLIC) ? "public" :
//                    clazz.getModifierList().hasModifierProperty(PsiModifier.PROTECTED) ? "protected" :
//                    clazz.getModifierList().hasModifierProperty(PsiModifier.PRIVATE) ? "private" : "package-private",
//                    clazz.getTextRange().getStartOffset(),
//                    clazz.getTextRange().getEndOffset(),
//                    getModifiers(clazz.getModifierList()),
//                    new HashMap<>(),
//                    clazz.getDocComment() != null ? clazz.getDocComment().getText() : "",
//                    new ArrayList<>()
//                );
//                classSymbol.setContent(clazz.getText());
//                metadata.getClasses().add(classSymbol);
//                super.visitClass(clazz);
//            }
//        });
//
//        return metadata;
//    }

    private List<String> getModifiers(PsiModifierList modifierList) {
        List<String> modifiers = new ArrayList<>();
        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add("public");
        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add("protected");
        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add("private");
        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) modifiers.add("static");
        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) modifiers.add("final");
        if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers.add("abstract");
        if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) modifiers.add("native");
        if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) modifiers.add("synchronized");
        if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) modifiers.add("transient");
        if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) modifiers.add("volatile");
        return modifiers;
    }

    private int countLines(PsiFile file) {
        return file.getText().split("\n").length;
    }

    private int countCommentLines(PsiFile file) {
        final int[] commentLines = {0};
        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitComment(PsiComment comment) {
                String[] lines = comment.getText().split("\n");
                commentLines[0] += lines.length;
                super.visitComment(comment);
            }
        });
        return commentLines[0];
    }

    private int calculateComplexity(PsiJavaFile file) {
        final int[] complexity = {0};
        file.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitIfStatement(PsiIfStatement statement) {
                complexity[0]++;
                super.visitIfStatement(statement);
            }

            @Override
            public void visitForStatement(PsiForStatement statement) {
                complexity[0]++;
                super.visitForStatement(statement);
            }

            @Override
            public void visitWhileStatement(PsiWhileStatement statement) {
                complexity[0]++;
                super.visitWhileStatement(statement);
            }

            @Override
            public void visitDoWhileStatement(PsiDoWhileStatement statement) {
                complexity[0]++;
                super.visitDoWhileStatement(statement);
            }

            @Override
            public void visitSwitchStatement(PsiSwitchStatement statement) {
                complexity[0]++;
                super.visitSwitchStatement(statement);
            }

            @Override
            public void visitCatchSection(PsiCatchSection section) {
                complexity[0]++;
                super.visitCatchSection(section);
            }
        });
        return complexity[0];
    }
} 