package com.yohannzhang.aigit.service;


import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.factory.AIServiceFactory;

import java.util.function.Consumer;

public class CodeService {
    private final AIService aiService;

    ApiKeySettings settings = ApiKeySettings.getInstance();

//    public CodeService() {
//        String selectedClient = settings.getSelectedClient();
//        this.aiService = getAIService(selectedClient);
//    }

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


    public CodeService() {
        String selectedClient = settings.getSelectedClient();
        this.aiService = AIServiceFactory.createAIService(selectedClient);
    }

}
