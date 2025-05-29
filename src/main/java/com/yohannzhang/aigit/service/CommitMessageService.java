package com.yohannzhang.aigit.service;


import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.impl.*;
import com.yohannzhang.aigit.util.PromptUtil;

import java.util.function.Consumer;

public class CommitMessageService {
    private final AIService aiService;

    ApiKeySettings settings = ApiKeySettings.getInstance();

    public CommitMessageService() {
        String selectedClient = settings.getSelectedClient();
        this.aiService = getAIService(selectedClient);
    }

    public boolean checkNecessaryModuleConfigIsRight() {
        return aiService.checkNecessaryModuleConfigIsRight();
    }

    public String generateCommitMessage(String diff) throws Exception {
        String prompt = PromptUtil.constructPrompt(diff);
        return aiService.generateCommitMessage(prompt);
    }

    public void generateCommitMessageStream(String diff, Consumer<String> onNext, Consumer<Throwable> onError,Runnable onComplete) throws Exception {
        String prompt = PromptUtil.constructPrompt(diff);
        System.out.println(prompt);
        aiService.generateCommitMessageStream(prompt, onNext, onError, onComplete);
    }

    public boolean generateByStream() {
        return aiService.generateByStream();
    }


    public static AIService getAIService(String selectedClient) {
        return switch (selectedClient) {
            case Constants.Ollama -> new OllamaService();
            case Constants.Gemini -> new GeminiService();
            case Constants.DeepSeek -> new DeepSeekAPIService();
            case Constants.OpenAI_API -> new OpenAIAPIService();
            case Constants.CloudflareWorkersAI -> new CloudflareWorkersAIService();
            case Constants.阿里云百炼 -> new AliYunBaiLianService();
            case Constants.SiliconFlow -> new SiliconFlowService();
            default -> throw new IllegalArgumentException("Invalid LLM client: " + selectedClient);
        };
    }

}
