package com.yohannzhang.aigit.service.impl;

import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.AIService;
import com.yohannzhang.aigit.util.OpenAIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * AliYunBaiLianService
 *
 * @author hmydk
 */
public class AliYunBaiLianService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(AliYunBaiLianService.class);

    @Override
    public boolean generateByStream() {
        return true;
    }

    @Override
    public String generateCommitMessage(String content) throws Exception {
        return "null";
    }

    @Override
    public void generateCommitMessageStream(String prompt, Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) throws Exception {
        try {
            OpenAIUtil.getAIResponseStream(Constants.阿里云百炼, prompt, onNext, onError, onComplete);
        } catch (Exception e) {
            if (OpenAIUtil.isCancelled()) {
                log.info("Request was cancelled");
                if (onError != null) {
                    onError.accept(new InterruptedException("Request was cancelled"));
                }
            } else {
                throw e;
            }
        }
    }

//    @Override
//    public void generateCommitMessageStream(String content, Consumer<String> onNext)
//            throws Exception {
//        OpenAIUtil.getAIResponseStream(Constants.阿里云百炼, content, onNext);
//    }

    @Override
    public boolean checkNecessaryModuleConfigIsRight() {
        return OpenAIUtil.checkNecessaryModuleConfigIsRight(Constants.阿里云百炼);
    }
}
