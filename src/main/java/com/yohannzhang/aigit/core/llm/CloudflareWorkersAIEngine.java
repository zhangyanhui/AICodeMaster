package com.yohannzhang.aigit.core.llm;

import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.util.OpenAIUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class CloudflareWorkersAIEngine extends AbstractLLMEngine {
    public CloudflareWorkersAIEngine(String apiKey, String apiEndpoint) {
        super(apiKey, apiEndpoint, Constants.CloudflareWorkersAI, new ModelInfo(
            "llama-2-70b-chat-int8",
            "1.0",
            "Cloudflare",
            32768,
            true
        ));
    }

    @Override
    public String generateText(String prompt, String context) {
        try {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = new CompletableFuture<>();

            OpenAIUtil.getAIResponseStream(
                Constants.CloudflareWorkersAI,
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
    public String generateCode(String prompt, String context, String language) {
        try {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = new CompletableFuture<>();

            String systemPrompt = "You are an expert programmer. Generate code in " + language + 
                                " that follows best practices and includes proper documentation.";
            String fullPrompt = systemPrompt + "\n\n" + context + "\n\n" + prompt;

            OpenAIUtil.getAIResponseStream(
                Constants.CloudflareWorkersAI,
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
    public Map<String, Object> analyzeCode(String code, String language) {
        try {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = new CompletableFuture<>();

            String systemPrompt = "You are a code analysis expert. Analyze the following code and provide detailed feedback.";
            String fullPrompt = systemPrompt + "\n\nLanguage: " + language + "\n\nCode:\n" + code;

            OpenAIUtil.getAIResponseStream(
                Constants.CloudflareWorkersAI,
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
    public void optimizeCode(String code, String language, List<String> suggestions, StreamCallback callback) {
        try {
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
                Constants.CloudflareWorkersAI,
                fullPrompt,
                callback::onToken,
                callback::onError,
                callback::onComplete
            );
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public void generateTests(String code, String language, String context, StreamCallback callback) {
        try {
            String systemPrompt = "你是一位测试专家。请根据以下代码结构分析生成全面的测试用例。";
            String fullPrompt = systemPrompt + "\n\n" + context + "\n\n" +
                    "编程语言: " + language + "\n\n" +
                    "代码:\n" + code;

            OpenAIUtil.getAIResponseStream(
                Constants.CloudflareWorkersAI,
                fullPrompt,
                callback::onToken,
                callback::onError,
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
                Constants.CloudflareWorkersAI,
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

    public int calculateCyclomaticComplexity(String code) {
        // 简单的圈复杂度计算
        int complexity = 1;
        String[] keywords = {"if", "else", "for", "while", "do", "switch", "case", "catch", "&&", "||", "?"};
        for (String keyword : keywords) {
            complexity += countOccurrences(code, keyword);
        }
        return complexity;
    }

    public int countNamingIssues(String code) {
        // 简单的命名问题检测
        int issues = 0;
        String[] patterns = {
            "\\b[a-z][a-zA-Z0-9]*\\s*\\(",  // 方法名以小写开头
            "\\b[A-Z][a-z0-9]*\\s*\\(",      // 类名以大写开头
            "\\b[a-z][a-zA-Z0-9]*\\s*="      // 变量名以小写开头
        };
        for (String pattern : patterns) {
            issues += code.split(pattern).length - 1;
        }
        return issues;
    }

    public int countOccurrences(String text, String pattern) {
        return text.split(pattern).length - 1;
    }
} 