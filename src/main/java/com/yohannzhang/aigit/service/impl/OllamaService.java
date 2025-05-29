package com.yohannzhang.aigit.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.AIService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OllamaService
 *
 * @author hmydk
 */
public class OllamaService implements AIService {
    private static volatile boolean isCancelled = false;

    @Override
    public boolean generateByStream() {
        return true;
    }

    public static void cancelRequest() {
        isCancelled = true;
    }

    // private static final Logger log =
    // LoggerFactory.getLogger(OllamaService.class);
    @Override
    public String generateCommitMessage(String content) throws Exception {

        ApiKeySettings settings = ApiKeySettings.getInstance();
        String selectedModule = settings.getSelectedModule();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(Constants.Ollama);
        String aiResponse = getAIResponse(selectedModule, moduleConfig.getUrl(), content);

        return aiResponse.replaceAll("```", "");
    }

    @Override
    public void generateCommitMessageStream(String content, Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) throws Exception {
        getAIResponseStream(content, onNext, onError, onComplete);
    }

    @Override
    public boolean checkNecessaryModuleConfigIsRight() {
        ApiKeySettings settings = ApiKeySettings.getInstance();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(Constants.Ollama);
        if (moduleConfig == null) {
            return false;
        }
        String selectedModule = settings.getSelectedModule();
        String url = moduleConfig.getUrl();
        return StringUtils.isNotEmpty(selectedModule) && StringUtils.isNotEmpty(url);
    }

    @Override
    public boolean validateConfig(Map<String, String> config) {
        int statusCode;
        try {
            HttpURLConnection connection = getHttpURLConnection(config.get("module"), config.get("url"), "hi");
            statusCode = connection.getResponseCode();
        } catch (IOException e) {
            return false;
        }
        // 打印状态码
        System.out.println("HTTP Status Code: " + statusCode);
        return statusCode == 200;
    }

    private static String getAIResponse(String module, String url, String textContent) throws Exception {
        HttpURLConnection connection = getHttpURLConnection(module, url, textContent);

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonResponse = objectMapper.readTree(response.toString());
        String rawResponse = jsonResponse.path("response").asText();
        return removeThinkTags(rawResponse);
    }

    private static String removeThinkTags(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        StringBuilder result = new StringBuilder();
        boolean inThinkTag = false;
        int i = 0;
        
        while (i < content.length()) {
            if (i + 6 < content.length() && content.substring(i, i + 6).equals("<think>")) {
                inThinkTag = true;
                i += 6;
                continue;
            }
            
            if (i + 7 < content.length() && content.substring(i, i + 7).equals("</think>")) {
                inThinkTag = false;
                i += 7;
                continue;
            }
            
            if (!inThinkTag) {
                result.append(content.charAt(i));
            }
            i++;
        }
        
        return result.toString().trim();
    }

    private static @NotNull HttpURLConnection getHttpURLConnection(String module, String url, String textContent)
            throws IOException {

        GenerateRequest request = new GenerateRequest(module, textContent, false);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonInputString = objectMapper.writeValueAsString(request);

        URI uri = URI.create(url);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        return connection;
    }

    private static class GenerateRequest {
        private String model;
        private String prompt;
        private boolean stream;

        public GenerateRequest(String model, String prompt, boolean stream) {
            this.model = model;
            this.prompt = prompt;
            this.stream = stream;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public boolean isStream() {
            return stream;
        }

        public void setStream(boolean stream) {
            this.stream = stream;
        }
    }

    private void getAIResponseStream(String textContent, Consumer<String> onNext,
                                     Consumer<Throwable> onError, Runnable onComplete) throws Exception {
        isCancelled = false;

        ApiKeySettings settings = ApiKeySettings.getInstance();
        String selectedModule = settings.getSelectedModule();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(Constants.Ollama);

        GenerateRequest request = new GenerateRequest(selectedModule, textContent, true);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonInputString = objectMapper.writeValueAsString(request);
        //定义一个字符串存储响应结果
        StringBuilder fullResponse = new StringBuilder();

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URI uri = URI.create(moduleConfig.getUrl());
                connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null && !isCancelled) {
                        JsonNode jsonResponse = objectMapper.readTree(line);
                        String response = jsonResponse.path("response").asText();
                        if (!response.isEmpty()) {
                            //拼接响应结果
                            fullResponse.append(response);
                            //判断响应结果中是否包含<think>内容</think>,如只包含<think>则跳过，若包含</think>,则取</think>之后的内容返回
                            if(fullResponse.toString().contains("<think>")&&!fullResponse.toString().contains("</think>")){
                                continue;
                            }
                          System.out.println("==="+fullResponse);


                            if (!response.isEmpty()) {
                                onNext.accept(response);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (!isCancelled) {
                    onError.accept(e);
                }
            } finally {
                onComplete.run();
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    // public static void main(String[] args) {
    // OllamaService ollamaService = new OllamaService();
    // String s = ollamaService.generateCommitMessage("你如何看待节假日调休这件事情？");
    // System.out.println(s);
    // }
}
