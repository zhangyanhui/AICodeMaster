package com.yohannzhang.aigit.service.impl;

import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.AIService;
import com.yohannzhang.aigit.util.OpenAIUtil;

import java.util.function.Consumer;

public class DeepSeekAPIService implements AIService {
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
//        OpenAIUtil.getAIResponseStream(Constants.DeepSeek, content, onNext);
//    }

    @Override
    public void generateCommitMessageStream(String prompt, Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) throws Exception {
        OpenAIUtil.getAIResponseStream(Constants.DeepSeek, prompt, onNext, onError, onComplete);
    }

    @Override
    public boolean checkNecessaryModuleConfigIsRight() {
        return OpenAIUtil.checkNecessaryModuleConfigIsRight(Constants.DeepSeek);
    }
}
