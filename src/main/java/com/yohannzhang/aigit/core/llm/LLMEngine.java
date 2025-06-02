package com.yohannzhang.aigit.core.llm;

import java.util.List;
import java.util.Map;

public interface LLMEngine {
    interface StreamCallback {
        void onStart();
        void onToken(String token);
        void onError(Throwable error);
        void onComplete();
    }

    /**
     * 获取模型信息
     * @return 模型信息
     */
    ModelInfo getModelInfo();

    /**
     * 生成文本
     * @param prompt 提示词
     * @param context 上下文
     * @return 生成的文本
     */
    String generateText(String prompt, String context);

    /**
     * 生成代码
     * @param prompt 提示词
     * @param context 上下文
     * @param language 目标语言
     * @return 生成的代码
     */
    String generateCode(String prompt, String context, String language);

    /**
     * 分析代码
     * @param code 源代码
     * @param language 编程语言
     * @return 分析结果
     */
    Map<String, Object> analyzeCode(String code, String language);

    /**
     * 优化代码
     * @param code 源代码
     * @param language 编程语言
     * @param suggestions 优化建议
     * @param callback 回调函数
     */
    void optimizeCode(String code, String language, List<String> suggestions, StreamCallback callback);

    /**
     * 生成测试用例
     * @param code 源代码
     * @param language 编程语言
     * @param context 上下文信息
     * @param callback 回调函数
     */
    void generateTests(String code, String language, String context, StreamCallback callback);

    /**
     * 生成代码文档
     * @param code 源代码
     * @param language 编程语言
     * @return 生成的文档
     */
    String generateDocumentation(String code, String language);

    /**
     * 流式生成代码文档
     * @param code 源代码
     * @param language 编程语言
     * @param callback 回调接口
     */
    void generateDocumentation(String code, String language, StreamCallback callback);

    /**
     * 分析代码复杂度
     * @param code 源代码
     * @param language 编程语言
     * @return 分析结果
     */
    Map<String, Object> analyzeComplexity(String code, String language);

    void generateText(String prompt, StreamCallback callback);
    void generateCode(String prompt, String code, String language, StreamCallback callback);
    void analyzeCode(String code, String language, StreamCallback callback);
    void optimizeCode(String code, String language, StreamCallback callback);
}