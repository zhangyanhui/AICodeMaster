package com.yohannzhang.aigit.service;


import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.impl.*;

import java.util.function.Consumer;

public class CodeService {
    private final AIService aiService;

    ApiKeySettings settings = ApiKeySettings.getInstance();

    public CodeService() {
        String selectedClient = settings.getSelectedClient();
        this.aiService = getAIService(selectedClient);
    }

    public boolean checkNecessaryModuleConfigIsRight() {
        return aiService.checkNecessaryModuleConfigIsRight();
    }

    public String generateCommitMessage(String prompt) throws Exception {
        //  String prompt = PromptUtil.constructPrompt(diff);
        return aiService.generateCommitMessage(prompt);
    }

    public void generateCommitMessageStream(String prompt, Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) throws Exception {
        aiService.generateCommitMessageStream(prompt, onNext, onError, onComplete);
    }


    public boolean generateByStream() {
        return aiService.generateByStream();
    }


    public static AIService getAIService(String selectedClient) {
        AIService service;
        switch (selectedClient) {
            case Constants.Ollama:
                service = new OllamaService();
                break;
            case Constants.Gemini:
                service = new GeminiService();
                break;
            case Constants.DeepSeek:
                service = new DeepSeekAPIService();
                break;
            case Constants.OpenAI_API:
                service = new OpenAIAPIService();
                break;
            case Constants.CloudflareWorkersAI:
                service = new CloudflareWorkersAIService();
                break;
            case Constants.阿里云百炼:
                service = new AliYunBaiLianService();
                break;
            case Constants.SiliconFlow:
                service = new SiliconFlowService();
                break;
            default:
                throw new IllegalArgumentException("Invalid LLM client: " + selectedClient);
        }
        return service;
    }

}
