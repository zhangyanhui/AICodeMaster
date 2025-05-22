package com.yohannzhang.aigit.service.impl;

import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.AIService;
import com.yohannzhang.aigit.util.OpenAIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * OpenAIAPIService
 *
 * @author hmydk
 */
public class OpenAIAPIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIAPIService.class);

    @Override
    public boolean generateByStream() {
        return true;
    }

    @Override
    public String generateCommitMessage(String content) throws Exception {
        return "null";
    }

//    @Override
//    public void generateCommitMessageStream(String content, Consumer<String> onNext) throws Exception {
//        OpenAIUtil.getAIResponseStream(Constants.OpenAI_API, content, onNext);
//    }
    @Override
    public void generateCommitMessageStream(String prompt, Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) throws Exception {
        OpenAIUtil.getAIResponseStream(Constants.OpenAI_API, prompt, onNext, onError, onComplete);
    }
    @Override
    public boolean checkNecessaryModuleConfigIsRight() {
        return OpenAIUtil.checkNecessaryModuleConfigIsRight(Constants.OpenAI_API);
    }
}
