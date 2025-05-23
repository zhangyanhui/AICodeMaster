package com.yohannzhang.aigit.action;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.yohannzhang.aigit.service.CodeService;
import org.jetbrains.annotations.NotNull;


import java.io.IOException;

/**
 * 代码补全
 * @author yanhuizhang
 * @date 2025/5/22 22:26
 */
public class AICodeCompletionContributor extends CompletionContributor {
//    private static final String API_URL = "https://api.openai.com/v1/completions";
//    private static final String API_KEY = "your-api-key";

    public AICodeCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                // 获取当前上下文
                PsiElement position = parameters.getPosition();
                String contextText = position.getText();

                // 调用大模型API获取补全建议
                String completion;
                try {
                    completion = getCompletionFromAI(contextText);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (completion != null) {
                    result.addElement(LookupElementBuilder.create(completion));
                }
            }
        });
    }

    private String getCompletionFromAI(String context) throws Exception {
        String promt = "你是一个Java代码专家，请根据给定的代码片段，进行代码补全。代码如下：" + context;

        CodeService codeService = new CodeService();

        return codeService.generateCommitMessage(promt);
//        OkHttpClient client = new OkHttpClient();
//        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
//        String jsonBody = "{\"model\": \"code-davinci-002\", \"prompt\": \"" + context + "\", \"max_tokens\": 50}";
//        RequestBody body = RequestBody.create(jsonBody, JSON);
//        Request request = new Request.Builder()
//                .url(API_URL)
//                .post(body)
//                .addHeader("Authorization", "Bearer " + API_KEY)
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            if (response.isSuccessful() && response.body() != null) {
//                String responseBody = response.body().string();
//                // 解析API响应，提取补全结果
//                return parseCompletion(responseBody);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return null;
    }

    private String parseCompletion(String responseBody) {
//        String result = responseBody.
        // 解析JSON响应，提取补全文本
        // 示例：假设响应格式为 {"choices": [{"text": "completionText"}]}
        // 实际解析逻辑需根据API响应格式调整
        return "completionText"; // 返回补全结果
    }
}
