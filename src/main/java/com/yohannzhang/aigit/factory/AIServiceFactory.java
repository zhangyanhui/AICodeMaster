package com.yohannzhang.aigit.factory;

import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.AIService;
import com.yohannzhang.aigit.service.impl.*;


public class AIServiceFactory {

    public static AIService createAIService(String selectedClient) {
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
