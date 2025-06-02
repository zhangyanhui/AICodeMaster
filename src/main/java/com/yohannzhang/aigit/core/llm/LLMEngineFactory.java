package com.yohannzhang.aigit.core.llm;

import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.config.ApiKeySettings;

public class LLMEngineFactory {
    public static LLMEngine createEngine(String selectedClient, ApiKeySettings.ModuleConfig moduleConfig) {
        if (moduleConfig == null) {
            throw new IllegalArgumentException("ModuleConfig cannot be null");
        }

        return switch (selectedClient) {
            case Constants.Gemini -> new GeminiEngine(moduleConfig.getApiKey(), moduleConfig.getUrl());
            case Constants.DeepSeek -> new DeepSeekEngine(moduleConfig.getApiKey(), moduleConfig.getUrl());
            case Constants.OpenAI_API -> new OpenAIEngine(moduleConfig.getApiKey(), moduleConfig.getUrl());
            case Constants.Ollama -> new OllamaEngine(moduleConfig.getApiKey(), moduleConfig.getUrl());
            case Constants.CloudflareWorkersAI -> new CloudflareWorkersAIEngine(moduleConfig.getApiKey(), moduleConfig.getUrl());
            case Constants.阿里云百炼 -> new AliyunTongyiEngine(moduleConfig.getApiKey(), moduleConfig.getUrl());
            case Constants.SiliconFlow -> new SiliconFlowEngine(moduleConfig.getApiKey(), moduleConfig.getUrl());
            default -> throw new IllegalStateException("Unsupported LLM client: " + selectedClient);
        };
    }
} 