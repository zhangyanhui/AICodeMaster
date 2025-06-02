package com.yohannzhang.aigit.core.llm;

import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.util.OpenAIUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractLLMEngine implements LLMEngine {
    protected final ModelInfo modelInfo;
    protected final String apiKey;
    protected final String apiEndpoint;
    protected final String clientName;

    protected AbstractLLMEngine(String apiKey, String apiEndpoint, String clientName, ModelInfo modelInfo) {
        this.apiKey = apiKey;
        this.apiEndpoint = apiEndpoint;
        this.clientName = clientName;
        this.modelInfo = modelInfo;
    }

    @Override
    public ModelInfo getModelInfo() {
        return modelInfo;
    }

    @Override
    public String generateText(String prompt, String context) {
        try {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = new CompletableFuture<>();

            OpenAIUtil.getAIResponseStream(
                clientName,
                context + "\n\n" + prompt,
                token -> result.updateAndGet(current -> current == null ? token : current + token),
                error -> future.completeExceptionally(error),
                () -> future.complete(null)
            );

            future.get();
            return result.get();
        } catch (Exception e) {
            throw new RuntimeException("Error generating text: " + e.getMessage(), e);
        }
    }

    @Override
    public void generateText(String prompt, StreamCallback callback) {
        try {
            callback.onStart();
            OpenAIUtil.getAIResponseStream(
                clientName,
                prompt,
                callback::onToken,
                e -> callback.onError((Exception) e),
                callback::onComplete
            );
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public String generateCode(String prompt, String context, String language) {
        try {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = new CompletableFuture<>();

            String systemPrompt = "You are an expert programmer. Generate code in " + language + 
                                " that follows best practices and includes proper documentation.";
            String fullPrompt = systemPrompt + "\n\n" + context + "\n\n" + prompt;

            OpenAIUtil.getAIResponseStream(
                clientName,
                fullPrompt,
                token -> result.updateAndGet(current -> current == null ? token : current + token),
                error -> future.completeExceptionally(error),
                () -> future.complete(null)
            );

            future.get();
            return result.get();
        } catch (Exception e) {
            throw new RuntimeException("Error generating code: " + e.getMessage(), e);
        }
    }

    @Override
    public void generateCode(String prompt, String code, String language, StreamCallback callback) {
        try {
            callback.onStart();
            String systemPrompt = "你是一位经验丰富的程序员，请生成符合最佳实践的" + language +
                    "代码，并包含必要的注释和文档说明。";
            String fullPrompt = systemPrompt + "\n\n" + code + "\n\n" + prompt;

            OpenAIUtil.getAIResponseStream(
                clientName,
                fullPrompt,
                callback::onToken,
                e -> callback.onError((Exception) e),
                callback::onComplete
            );
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public Map<String, Object> analyzeCode(String code, String language) {
        try {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = new CompletableFuture<>();

            String systemPrompt = "You are a code analysis expert. Analyze the following code and provide detailed feedback.";
            String fullPrompt = systemPrompt + "\n\nLanguage: " + language + "\n\nCode:\n" + code;

            OpenAIUtil.getAIResponseStream(
                clientName,
                fullPrompt,
                token -> result.updateAndGet(current -> current == null ? token : current + token),
                error -> future.completeExceptionally(error),
                () -> future.complete(null)
            );

            future.get();
            
            Map<String, Object> analysisResult = new HashMap<>();
            analysisResult.put("analysis", result.get());
            analysisResult.put("language", language);
            analysisResult.put("timestamp", new Date());
            
            return analysisResult;
        } catch (Exception e) {
            throw new RuntimeException("Error analyzing code: " + e.getMessage(), e);
        }
    }

    @Override
    public void analyzeCode(String code, String language, StreamCallback callback) {
        try {
            callback.onStart();
            String systemPrompt = "You are a code analysis expert. Analyze the following code and provide detailed feedback.";
            String fullPrompt = systemPrompt + "\n\nLanguage: " + language + "\n\nCode:\n" + code;

            OpenAIUtil.getAIResponseStream(
                clientName,
                fullPrompt,
                callback::onToken,
                e -> callback.onError((Exception) e),
                callback::onComplete
            );
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public void optimizeCode(String code, String language, List<String> suggestions, StreamCallback callback) {
        try {
            callback.onStart();
            String systemPrompt = "你是一位代码优化专家。请根据以下建议和代码质量分析结果优化下面的代码。";
            String fullPrompt = systemPrompt + "\n\n编程语言: " + language + "\n\n" +
                    "代码质量分析:\n" +
                    "- 总行数: " + code.split("\n").length + "\n" +
                    "- 圈复杂度: " + calculateCyclomaticComplexity(code) + "\n" +
                    "- 命名问题数: " + countNamingIssues(code) + "\n\n" +
                    "优化建议:\n" +
                    String.join("\n", suggestions.stream().map(s -> "- " + s).toList()) + "\n\n" +
                    "代码:\n" + code;

            OpenAIUtil.getAIResponseStream(
                clientName,
                fullPrompt,
                callback::onToken,
                e -> callback.onError((Exception) e),
                callback::onComplete
            );
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public void optimizeCode(String code, String language, StreamCallback callback) {
        try {
            callback.onStart();
            String systemPrompt = "你是一位代码优化专家。请分析并优化下面的代码。";
            String fullPrompt = systemPrompt + "\n\n编程语言: " + language + "\n\n" +
                    "代码质量分析:\n" +
                    "- 总行数: " + code.split("\n").length + "\n" +
                    "- 圈复杂度: " + calculateCyclomaticComplexity(code) + "\n" +
                    "- 命名问题数: " + countNamingIssues(code) + "\n\n" +
                    "代码:\n" + code;

            OpenAIUtil.getAIResponseStream(
                clientName,
                fullPrompt,
                callback::onToken,
                e -> callback.onError((Exception) e),
                callback::onComplete
            );
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public void generateTests(String code, String language, String context, StreamCallback callback) {
        try {
            callback.onStart();
            String systemPrompt = "你是一位测试专家。请根据以下代码结构分析生成全面的测试用例。";
            String fullPrompt = systemPrompt + "\n\n" + context + "\n\n" +
                    "编程语言: " + language + "\n\n" +
                    "代码:\n" + code;

            OpenAIUtil.getAIResponseStream(
                clientName,
                fullPrompt,
                callback::onToken,
                e -> callback.onError((Exception) e),
                callback::onComplete
            );
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public String generateDocumentation(String code, String language) {
        try {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = new CompletableFuture<>();

            String systemPrompt = "You are a documentation expert. Generate comprehensive documentation for the following code.";
            String fullPrompt = systemPrompt + "\n\nLanguage: " + language + "\n\nCode:\n" + code;

            OpenAIUtil.getAIResponseStream(
                clientName,
                fullPrompt,
                token -> result.updateAndGet(current -> current == null ? token : current + token),
                error -> future.completeExceptionally(error),
                () -> future.complete(null)
            );

            future.get();
            return result.get();
        } catch (Exception e) {
            throw new RuntimeException("Error generating documentation: " + e.getMessage(), e);
        }
    }

    @Override
    public void generateDocumentation(String code, String language, StreamCallback callback) {
        try {
            callback.onStart();
            String systemPrompt = "你是一个文档专家。请为以下代码生成详细的文档说明。";
            String fullPrompt = systemPrompt + "\n\n语言: " + language + "\n\n代码:\n" + code;

            OpenAIUtil.getAIResponseStream(
                clientName,
                fullPrompt,
                callback::onToken,
                e -> callback.onError((Exception) e),
                callback::onComplete
            );
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public Map<String, Object> analyzeComplexity(String code, String language) {
        Map<String, Object> result = new HashMap<>();
        result.put("cyclomaticComplexity", calculateCyclomaticComplexity(code));
        result.put("maxNestingDepth", calculateMaxNestingDepth(code));
        result.put("namingIssues", countNamingIssues(code));
        return result;
    }

    protected int calculateCyclomaticComplexity(String code) {
        int complexity = 1; // Base complexity
        
        // Count control flow statements
        String[] patterns = {
            "\\bif\\b", "\\belse\\b", "\\bfor\\b", "\\bwhile\\b", "\\bdo\\b",
            "\\bswitch\\b", "\\bcase\\b", "\\bcatch\\b", "\\b&&\\b", "\\b\\|\\|\\b", "\\?"
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(code);
            while (m.find()) {
                complexity++;
            }
        }
        
        return complexity;
    }

    protected int calculateMaxNestingDepth(String code) {
        int maxDepth = 0;
        int currentDepth = 0;
        
        for (char c : code.toCharArray()) {
            if (c == '{') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == '}') {
                currentDepth--;
            }
        }
        
        return maxDepth;
    }

    protected int countNamingIssues(String code) {
        int issues = 0;
        
        // Check for single letter variable names (except loop variables)
        Pattern singleLetterPattern = Pattern.compile("\\b[a-z]\\b(?!\\s*[=;])");
        Matcher singleLetterMatcher = singleLetterPattern.matcher(code);
        while (singleLetterMatcher.find()) {
            issues++;
        }
        
        // Check for non-descriptive names
        Pattern nonDescriptivePattern = Pattern.compile("\\b(var|temp|data|value|result)\\b");
        Matcher nonDescriptiveMatcher = nonDescriptivePattern.matcher(code);
        while (nonDescriptiveMatcher.find()) {
            issues++;
        }
        
        return issues;
    }
} 