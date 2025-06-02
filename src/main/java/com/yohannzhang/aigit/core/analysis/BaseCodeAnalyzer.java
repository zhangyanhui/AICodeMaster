package com.yohannzhang.aigit.core.analysis;

import com.yohannzhang.aigit.core.models.FileMetadata;
import com.yohannzhang.aigit.core.models.Project;
import com.yohannzhang.aigit.core.models.Symbol;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BaseCodeAnalyzer implements CodeAnalyzer {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".java", ".kt", ".groovy", ".scala",  // JVM languages
        ".py", ".js", ".ts", ".jsx", ".tsx",  // Scripting languages
        ".cpp", ".c", ".h", ".hpp",           // C/C++
        ".go", ".rs", ".swift"                // Other languages
    );

    @Override
    public Project analyzeProject(Path projectPath) {
        String projectId = UUID.randomUUID().toString();
        String projectName = projectPath.getFileName().toString();
        Project project = new Project(projectId, projectName, projectPath);

        try {
            Files.walkFileTree(projectPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isSourceFile(file)) {
                        try {
                            FileMetadata metadata = analyzeFile(file);
                            project.addFile(metadata);
                        } catch (IOException e) {
                            System.err.println("Error analyzing file: " + file);
                            e.printStackTrace();
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // 分析项目统计信息
            analyzeProjectStats(project);
            
            return project;
        } catch (IOException e) {
            throw new RuntimeException("Error analyzing project: " + projectPath, e);
        }
    }

    @Override
    public FileMetadata analyzeFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String language = getLanguage(filePath);
        
        Map<String, List<Symbol>> symbols = extractSymbols(content, language);
        List<String> dependencies = extractDependencies(content, language);
        String summary = generateSummary(content, language);
        
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        String contentHash = String.valueOf(content.hashCode());
        
        return new FileMetadata(
            filePath.toString(),
            language,
            attrs.size(),
            (int) content.lines().count(),
            LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), java.time.ZoneId.systemDefault()),
            contentHash,
            symbols,
            dependencies,
            summary
        );
    }

    @Override
    public Map<String, List<Symbol>> extractSymbols(String content, String language) {
        Map<String, List<Symbol>> symbols = new HashMap<>();
        List<String> lines = content.lines().collect(Collectors.toList());
        
        switch (language.toLowerCase()) {
            case "java":
                extractJavaSymbols(lines, symbols);
                break;
            case "python":
                extractPythonSymbols(lines, symbols);
                break;
            case "javascript":
            case "typescript":
                extractJSSymbols(lines, symbols);
                break;
            default:
                // 对于其他语言，使用通用符号提取
                extractGenericSymbols(lines, symbols);
        }
        
        return symbols;
    }

    private void extractJavaSymbols(List<String> lines, Map<String, List<Symbol>> symbols) {
        List<Symbol> classes = new ArrayList<>();
        List<Symbol> methods = new ArrayList<>();
        List<Symbol> fields = new ArrayList<>();
        
        Pattern classPattern = Pattern.compile("(public|private|protected)?\\s*(abstract|final)?\\s*class\\s+(\\w+)");
        Pattern methodPattern = Pattern.compile("(public|private|protected)?\\s*(static|abstract|final)?\\s*(\\w+)\\s+(\\w+)\\s*\\([^)]*\\)");
        Pattern fieldPattern = Pattern.compile("(public|private|protected)?\\s*(static|final)?\\s*(\\w+)\\s+(\\w+)\\s*[=;]");
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            
            // 提取类
            Matcher classMatcher = classPattern.matcher(line);
            if (classMatcher.find()) {
                String visibility = classMatcher.group(1) != null ? classMatcher.group(1) : "package-private";
                String modifiers = classMatcher.group(2) != null ? classMatcher.group(2) : "";
                String name = classMatcher.group(3);
                
                classes.add(new Symbol(
                    name,
                    "class",
                    visibility,
                    i + 1,
                    findEndOfBlock(lines, i),
                    Arrays.asList(modifiers.split("\\s+")),
                    new HashMap<>(),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
            
            // 提取方法
            Matcher methodMatcher = methodPattern.matcher(line);
            if (methodMatcher.find()) {
                String visibility = methodMatcher.group(1) != null ? methodMatcher.group(1) : "package-private";
                String modifiers = methodMatcher.group(2) != null ? methodMatcher.group(2) : "";
                String returnType = methodMatcher.group(3);
                String name = methodMatcher.group(4);
                
                methods.add(new Symbol(
                    name,
                    "method",
                    visibility,
                    i + 1,
                    findEndOfBlock(lines, i),
                    Arrays.asList(modifiers.split("\\s+")),
                    Map.of("returnType", returnType),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
            
            // 提取字段
            Matcher fieldMatcher = fieldPattern.matcher(line);
            if (fieldMatcher.find()) {
                String visibility = fieldMatcher.group(1) != null ? fieldMatcher.group(1) : "package-private";
                String modifiers = fieldMatcher.group(2) != null ? fieldMatcher.group(2) : "";
                String type = fieldMatcher.group(3);
                String name = fieldMatcher.group(4);
                
                fields.add(new Symbol(
                    name,
                    "field",
                    visibility,
                    i + 1,
                    i + 1,
                    Arrays.asList(modifiers.split("\\s+")),
                    Map.of("type", type),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
        }
        
        symbols.put("class", classes);
        symbols.put("method", methods);
        symbols.put("field", fields);
    }

    private void extractPythonSymbols(List<String> lines, Map<String, List<Symbol>> symbols) {
        List<Symbol> classes = new ArrayList<>();
        List<Symbol> methods = new ArrayList<>();
        List<Symbol> functions = new ArrayList<>();
        
        Pattern classPattern = Pattern.compile("class\\s+(\\w+)(?:\\(([^)]+)\\))?\\s*:");
        Pattern methodPattern = Pattern.compile("def\\s+(\\w+)\\s*\\([^)]*\\)\\s*:");
        Pattern functionPattern = Pattern.compile("def\\s+(\\w+)\\s*\\([^)]*\\)\\s*:");
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            
            // 提取类
            Matcher classMatcher = classPattern.matcher(line);
            if (classMatcher.find()) {
                String name = classMatcher.group(1);
                String parent = classMatcher.group(2);
                
                classes.add(new Symbol(
                    name,
                    "class",
                    "public",
                    i + 1,
                    findEndOfPythonBlock(lines, i),
                    new ArrayList<>(),
                    parent != null ? Map.of("parent", parent) : new HashMap<>(),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
            
            // 提取方法
            Matcher methodMatcher = methodPattern.matcher(line);
            if (methodMatcher.find() && isInsideClass(lines, i)) {
                String name = methodMatcher.group(1);
                
                methods.add(new Symbol(
                    name,
                    "method",
                    "public",
                    i + 1,
                    findEndOfPythonBlock(lines, i),
                    new ArrayList<>(),
                    new HashMap<>(),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
            
            // 提取函数
            Matcher functionMatcher = functionPattern.matcher(line);
            if (functionMatcher.find() && !isInsideClass(lines, i)) {
                String name = functionMatcher.group(1);
                
                functions.add(new Symbol(
                    name,
                    "function",
                    "public",
                    i + 1,
                    findEndOfPythonBlock(lines, i),
                    new ArrayList<>(),
                    new HashMap<>(),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
        }
        
        symbols.put("class", classes);
        symbols.put("method", methods);
        symbols.put("function", functions);
    }

    private void extractJSSymbols(List<String> lines, Map<String, List<Symbol>> symbols) {
        List<Symbol> classes = new ArrayList<>();
        List<Symbol> methods = new ArrayList<>();
        List<Symbol> functions = new ArrayList<>();
        
        Pattern classPattern = Pattern.compile("(?:class|interface)\\s+(\\w+)(?:\\s+extends\\s+([^{]+))?\\s*\\{");
        Pattern methodPattern = Pattern.compile("(?:async\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*\\{");
        Pattern functionPattern = Pattern.compile("(?:const|let|var|function)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?\\([^)]*\\)\\s*=>");
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            
            // 提取类
            Matcher classMatcher = classPattern.matcher(line);
            if (classMatcher.find()) {
                String name = classMatcher.group(1);
                String parent = classMatcher.group(2);
                
                classes.add(new Symbol(
                    name,
                    "class",
                    "public",
                    i + 1,
                    findEndOfBlock(lines, i),
                    new ArrayList<>(),
                    parent != null ? Map.of("parent", parent) : new HashMap<>(),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
            
            // 提取方法
            Matcher methodMatcher = methodPattern.matcher(line);
            if (methodMatcher.find() && isInsideClass(lines, i)) {
                String name = methodMatcher.group(1);
                
                methods.add(new Symbol(
                    name,
                    "method",
                    "public",
                    i + 1,
                    findEndOfBlock(lines, i),
                    new ArrayList<>(),
                    new HashMap<>(),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
            
            // 提取函数
            Matcher functionMatcher = functionPattern.matcher(line);
            if (functionMatcher.find() && !isInsideClass(lines, i)) {
                String name = functionMatcher.group(1);
                
                functions.add(new Symbol(
                    name,
                    "function",
                    "public",
                    i + 1,
                    findEndOfBlock(lines, i),
                    new ArrayList<>(),
                    new HashMap<>(),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
        }
        
        symbols.put("class", classes);
        symbols.put("method", methods);
        symbols.put("function", functions);
    }

    private void extractGenericSymbols(List<String> lines, Map<String, List<Symbol>> symbols) {
        List<Symbol> functions = new ArrayList<>();
        
        // 通用函数提取模式
        Pattern functionPattern = Pattern.compile("(?:function|def|fn)\\s+(\\w+)\\s*\\([^)]*\\)");
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            
            Matcher functionMatcher = functionPattern.matcher(line);
            if (functionMatcher.find()) {
                String name = functionMatcher.group(1);
                
                functions.add(new Symbol(
                    name,
                    "function",
                    "public",
                    i + 1,
                    findEndOfBlock(lines, i),
                    new ArrayList<>(),
                    new HashMap<>(),
                    extractDocumentation(lines, i),
                    new ArrayList<>()
                ));
            }
        }
        
        symbols.put("function", functions);
    }

    private int findEndOfBlock(List<String> lines, int startLine) {
        int braceCount = 0;
        for (int i = startLine; i < lines.size(); i++) {
            String line = lines.get(i);
            braceCount += countChar(line, '{');
            braceCount -= countChar(line, '}');
            if (braceCount == 0) {
                return i + 1;
            }
        }
        return lines.size();
    }

    private int findEndOfPythonBlock(List<String> lines, int startLine) {
        int indent = getIndent(lines.get(startLine));
        for (int i = startLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) continue;
            if (getIndent(line) <= indent) {
                return i;
            }
        }
        return lines.size();
    }

    private int getIndent(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') count++;
            else break;
        }
        return count;
    }

    private int countChar(String str, char c) {
        return (int) str.chars().filter(ch -> ch == c).count();
    }

    private boolean isInsideClass(List<String> lines, int lineIndex) {
        for (int i = lineIndex; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.startsWith("class ") || line.startsWith("interface ")) {
                return true;
            }
        }
        return false;
    }

    private String extractDocumentation(List<String> lines, int lineIndex) {
        StringBuilder doc = new StringBuilder();
        for (int i = lineIndex - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*") || line.startsWith("#")) {
                doc.insert(0, line + "\n");
            } else {
                break;
            }
        }
        return doc.toString().trim();
    }

    @Override
    public List<String> extractDependencies(String content, String language) {
        List<String> dependencies = new ArrayList<>();
        
        switch (language.toLowerCase()) {
            case "java":
                extractJavaDependencies(content, dependencies);
                break;
            case "python":
                extractPythonDependencies(content, dependencies);
                break;
            case "javascript":
            case "typescript":
                extractJSDependencies(content, dependencies);
                break;
            default:
                // 对于其他语言，使用通用依赖提取
                extractGenericDependencies(content, dependencies);
        }
        
        return dependencies;
    }

    private void extractJavaDependencies(String content, List<String> dependencies) {
        Pattern importPattern = Pattern.compile("import\\s+(?:static\\s+)?([\\w.]+)(?:\\s*;|\\s+\\*)");
        Matcher matcher = importPattern.matcher(content);
        while (matcher.find()) {
            dependencies.add(matcher.group(1));
        }
    }

    private void extractPythonDependencies(String content, List<String> dependencies) {
        Pattern importPattern = Pattern.compile("(?:from\\s+([\\w.]+)\\s+import|import\\s+([\\w.]+))");
        Matcher matcher = importPattern.matcher(content);
        while (matcher.find()) {
            String dep = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            dependencies.add(dep);
        }
    }

    private void extractJSDependencies(String content, List<String> dependencies) {
        Pattern importPattern = Pattern.compile("(?:import\\s+(?:\\{[^}]+\\}\\s+from\\s+)?['\"]([^'\"]+)['\"]|require\\s*\\(['\"]([^'\"]+)['\"]\\))");
        Matcher matcher = importPattern.matcher(content);
        while (matcher.find()) {
            String dep = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            dependencies.add(dep);
        }
    }

    private void extractGenericDependencies(String content, List<String> dependencies) {
        // 通用依赖提取模式
        Pattern importPattern = Pattern.compile("(?:import|require|include|using)\\s+['\"]([^'\"]+)['\"]");
        Matcher matcher = importPattern.matcher(content);
        while (matcher.find()) {
            dependencies.add(matcher.group(1));
        }
    }

    @Override
    public String generateSummary(String content, String language) {
        StringBuilder summary = new StringBuilder();
        List<String> lines = content.lines().collect(Collectors.toList());
        
        // 提取文件头部注释
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*") || line.startsWith("#")) {
                summary.append(line).append("\n");
            } else if (!line.isEmpty()) {
                break;
            }
        }
        
        // 提取主要类和函数
        Map<String, List<Symbol>> symbols = extractSymbols(content, language);
        
        // 添加类信息
        if (symbols.containsKey("class")) {
            summary.append("\nClasses:\n");
            for (Symbol symbol : symbols.get("class")) {
                summary.append("- ").append(symbol.getName())
                      .append(": ").append(symbol.getDocumentation())
                      .append("\n");
            }
        }
        
        // 添加函数信息
        if (symbols.containsKey("function")) {
            summary.append("\nFunctions:\n");
            for (Symbol symbol : symbols.get("function")) {
                summary.append("- ").append(symbol.getName())
                      .append(": ").append(symbol.getDocumentation())
                      .append("\n");
            }
        }
        
        return summary.toString().trim();
    }

    @Override
    public Map<String, Object> analyzeCodeQuality(String content, String language) {
        Map<String, Object> metrics = new HashMap<>();
        
        // 计算代码行数
        int totalLines = (int) content.lines().count();
        int codeLines = (int) content.lines()
            .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("//") && !line.trim().startsWith("/*"))
            .count();
        int commentLines = totalLines - codeLines;
        
        metrics.put("totalLines", totalLines);
        metrics.put("codeLines", codeLines);
        metrics.put("commentLines", commentLines);
        metrics.put("commentRatio", (double) commentLines / totalLines);
        
        // 计算圈复杂度
        Map<String, Object> complexity = analyzeComplexity(content, language);
        metrics.putAll(complexity);
        
        // 计算命名规范
        Map<String, List<Symbol>> symbols = extractSymbols(content, language);
        int namingIssues = 0;
        for (List<Symbol> symbolList : symbols.values()) {
            for (Symbol symbol : symbolList) {
                if (!isValidNaming(symbol.getName(), symbol.getType(), language)) {
                    namingIssues++;
                }
            }
        }
        metrics.put("namingIssues", namingIssues);
        
        return metrics;
    }

    @Override
    public Map<String, Object> analyzeComplexity(String content, String language) {
        Map<String, Object> metrics = new HashMap<>();
        List<String> lines = content.lines().collect(Collectors.toList());
        
        // 计算圈复杂度
        int cyclomaticComplexity = 0;
        Pattern controlFlowPattern = Pattern.compile("(if|else|for|while|switch|case|catch|\\?|&&|\\|\\|)");
        
        for (String line : lines) {
            Matcher matcher = controlFlowPattern.matcher(line);
            while (matcher.find()) {
                cyclomaticComplexity++;
            }
        }
        
        metrics.put("cyclomaticComplexity", cyclomaticComplexity);
        
        // 计算嵌套深度
        int maxNestingDepth = 0;
        int currentNesting = 0;
        
        for (String line : lines) {
            currentNesting += countChar(line, '{');
            currentNesting -= countChar(line, '}');
            maxNestingDepth = Math.max(maxNestingDepth, currentNesting);
        }
        
        metrics.put("maxNestingDepth", maxNestingDepth);
        
        return metrics;
    }

    @Override
    public List<Map<String, Object>> analyzeDuplication(Project project) {
        List<Map<String, Object>> duplications = new ArrayList<>();
        Map<String, String> fileContents = new HashMap<>();
        
        // 收集所有文件内容
        for (Map.Entry<String, FileMetadata> entry : project.getFiles().entrySet()) {
            try {
                String content = Files.readString(Paths.get(entry.getKey()));
                fileContents.put(entry.getKey(), content);
            } catch (IOException e) {
                System.err.println("Error reading file: " + entry.getKey());
            }
        }
        
        // 比较文件内容
        for (Map.Entry<String, String> entry1 : fileContents.entrySet()) {
            for (Map.Entry<String, String> entry2 : fileContents.entrySet()) {
                if (entry1.getKey().compareTo(entry2.getKey()) < 0) { // 避免重复比较
                    double similarity = calculateSimilarity(entry1.getValue(), entry2.getValue());
                    if (similarity > 0.8) { // 相似度阈值
                        Map<String, Object> duplication = new HashMap<>();
                        duplication.put("file1", entry1.getKey());
                        duplication.put("file2", entry2.getKey());
                        duplication.put("similarity", similarity);
                        duplications.add(duplication);
                    }
                }
            }
        }
        
        return duplications;
    }

    @Override
    public Map<String, List<String>> analyzeDependencies(Project project) {
        Map<String, List<String>> dependencies = new HashMap<>();
        
        // 收集所有文件的依赖
        for (Map.Entry<String, FileMetadata> entry : project.getFiles().entrySet()) {
            String filePath = entry.getKey();
            FileMetadata metadata = entry.getValue();
            dependencies.put(filePath, metadata.getDependencies());
        }
        
        return dependencies;
    }

    private boolean isValidNaming(String name, String type, String language) {
        switch (language.toLowerCase()) {
            case "java":
                return isValidJavaNaming(name, type);
            case "python":
                return isValidPythonNaming(name, type);
            case "javascript":
            case "typescript":
                return isValidJSNaming(name, type);
            default:
                return true;
        }
    }

    private boolean isValidJavaNaming(String name, String type) {
        switch (type) {
            case "class":
                return name.matches("^[A-Z][a-zA-Z0-9]*$");
            case "method":
            case "function":
                return name.matches("^[a-z][a-zA-Z0-9]*$");
            case "field":
                return name.matches("^[a-z][a-zA-Z0-9]*$");
            default:
                return true;
        }
    }

    private boolean isValidPythonNaming(String name, String type) {
        switch (type) {
            case "class":
                return name.matches("^[A-Z][a-zA-Z0-9]*$");
            case "method":
            case "function":
                return name.matches("^[a-z][a-z0-9_]*$");
            default:
                return true;
        }
    }

    private boolean isValidJSNaming(String name, String type) {
        switch (type) {
            case "class":
                return name.matches("^[A-Z][a-zA-Z0-9]*$");
            case "method":
            case "function":
                return name.matches("^[a-z][a-zA-Z0-9]*$");
            default:
                return true;
        }
    }

    private double calculateSimilarity(String content1, String content2) {
        // 使用最长公共子序列算法计算相似度
        int[][] lcs = new int[content1.length() + 1][content2.length() + 1];
        
        for (int i = 1; i <= content1.length(); i++) {
            for (int j = 1; j <= content2.length(); j++) {
                if (content1.charAt(i - 1) == content2.charAt(j - 1)) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }
        
        int lcsLength = lcs[content1.length()][content2.length()];
        return (double) (2 * lcsLength) / (content1.length() + content2.length());
    }

    private boolean isSourceFile(Path file) {
        String fileName = file.toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private String getLanguage(Path file) {
        String fileName = file.toString().toLowerCase();
        if (fileName.endsWith(".java")) return "java";
        if (fileName.endsWith(".kt")) return "kotlin";
        if (fileName.endsWith(".py")) return "python";
        if (fileName.endsWith(".js")) return "javascript";
        if (fileName.endsWith(".ts")) return "typescript";
        if (fileName.endsWith(".cpp") || fileName.endsWith(".hpp")) return "cpp";
        if (fileName.endsWith(".c") || fileName.endsWith(".h")) return "c";
        if (fileName.endsWith(".go")) return "go";
        if (fileName.endsWith(".rs")) return "rust";
        if (fileName.endsWith(".swift")) return "swift";
        return "unknown";
    }

    private void analyzeProjectStats(Project project) {
        Map<String, Object> stats = new HashMap<>();
        
        // 语言统计
        Map<String, Long> languageStats = project.getFiles().values().stream()
            .collect(Collectors.groupingBy(FileMetadata::getLanguage, Collectors.counting()));
        stats.put("languages", languageStats);
        
        // 文件统计
        stats.put("totalFiles", project.getFiles().size());
        stats.put("totalLines", project.getFiles().values().stream()
            .mapToLong(FileMetadata::getLines)
            .sum());
        
        // 符号统计
        Map<String, Long> symbolStats = project.getFiles().values().stream()
            .flatMap(f -> f.getSymbols().values().stream())
            .flatMap(List::stream)
            .collect(Collectors.groupingBy(Symbol::getType, Collectors.counting()));
        stats.put("symbols", symbolStats);
        
        project.addStat("stats", stats);
    }

    @Override
    public Map<String, Object> analyzeCodeStructure(String code, String language) {
        Map<String, Object> structure = new HashMap<>();
        List<String> lines = code.lines().collect(Collectors.toList());
        
        // 提取类名
        String className = extractClassName(lines, language);
        structure.put("className", className);
        
        // 计算方法数
        int methodCount = countMethods(lines, language);
        structure.put("methodCount", methodCount);
        
        // 提取依赖项
        List<String> dependencies = extractDependencies(code, language);
        structure.put("dependencies", dependencies);
        
        // 分析代码结构
        Map<String, List<Symbol>> symbols = extractSymbols(code, language);
        structure.put("symbols", symbols);
        
        // 计算代码复杂度
        Map<String, Object> complexity = analyzeComplexity(code, language);
        structure.put("complexity", complexity);
        
        return structure;
    }

    private String extractClassName(List<String> lines, String language) {
        switch (language.toLowerCase()) {
            case "java":
                for (String line : lines) {
                    if (line.matches(".*class\\s+\\w+.*")) {
                        return line.replaceAll(".*class\\s+(\\w+).*", "$1");
                    }
                }
                break;
            case "python":
                for (String line : lines) {
                    if (line.matches(".*class\\s+\\w+.*")) {
                        return line.replaceAll(".*class\\s+(\\w+).*", "$1");
                    }
                }
                break;
            case "javascript":
            case "typescript":
                for (String line : lines) {
                    if (line.matches(".*class\\s+\\w+.*")) {
                        return line.replaceAll(".*class\\s+(\\w+).*", "$1");
                    }
                }
                break;
        }
        return "Unknown";
    }

    private int countMethods(List<String> lines, String language) {
        int count = 0;
        switch (language.toLowerCase()) {
            case "java":
                for (String line : lines) {
                    if (line.matches(".*(public|private|protected|static|final|abstract)\\s+\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{?")) {
                        count++;
                    }
                }
                break;
            case "python":
                for (String line : lines) {
                    if (line.matches(".*def\\s+\\w+\\s*\\([^)]*\\)\\s*:")) {
                        count++;
                    }
                }
                break;
            case "javascript":
            case "typescript":
                for (String line : lines) {
                    if (line.matches(".*(async\\s+)?\\w+\\s*\\([^)]*\\)\\s*\\{?")) {
                        count++;
                    }
                }
                break;
        }
        return count;
    }
} 