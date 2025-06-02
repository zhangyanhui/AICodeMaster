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

public class QwenEngine extends AbstractLLMEngine {
    public QwenEngine(String apiKey, String apiEndpoint) {
        super(apiKey, apiEndpoint, Constants.阿里云百炼, new ModelInfo(
            "qwen3:30b-a3b",
            "1.0",
            "Qwen",
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
                Constants.阿里云百炼,
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
                Constants.阿里云百炼,
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
                Constants.阿里云百炼,
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
            callback.onStart();
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("你是一位代码优化专家。请根据以下建议和代码质量分析结果优化下面的代码。\n\n");
            promptBuilder.append("编程语言: ").append(language).append("\n\n");
            promptBuilder.append("代码质量分析:\n");
            promptBuilder.append("- 总行数: ").append(code.split("\n").length).append("\n");
            promptBuilder.append("- 圈复杂度: ").append(calculateCyclomaticComplexity(code)).append("\n");
            promptBuilder.append("- 命名问题数: ").append(countNamingIssues(code)).append("\n\n");
            
            promptBuilder.append("优化建议:\n");
            for (String suggestion : suggestions) {
                promptBuilder.append("- ").append(suggestion).append("\n");
            }
            promptBuilder.append("\n代码:\n").append(code);

            OpenAIUtil.getAIResponseStream(
                Constants.阿里云百炼,
                promptBuilder.toString(),
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
                    Constants.阿里云百炼,
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
                    Constants.阿里云百炼,
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

    public int calculateCyclomaticComplexity(String code) {
        // 简单的圈复杂度计算
        int complexity = 1;
        // 使用转义后的正则表达式模式
        String[] patterns = {
            "\\bif\\b",
            "\\belse\\b",
            "\\bfor\\b",
            "\\bwhile\\b",
            "\\bdo\\b",
            "\\bswitch\\b",
            "\\bcase\\b",
            "\\bcatch\\b",
            "&&",
            "\\|\\|",
            "\\?"
        };
        for (String pattern : patterns) {
            complexity += countOccurrences(code, pattern);
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

    private int countOccurrences(String text, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private int countChar(String text, char ch) {
        return (int) text.chars().filter(c -> c == ch).count();
    }
} 