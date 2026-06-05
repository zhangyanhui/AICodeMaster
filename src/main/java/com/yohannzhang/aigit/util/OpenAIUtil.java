package com.yohannzhang.aigit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.pojo.OpenAIRequestBO;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class OpenAIUtil {

    private static volatile boolean isCancelled = false;

    public static boolean checkNecessaryModuleConfigIsRight(String client) {
        ApiKeySettings settings = ApiKeySettings.getInstance();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(client);
        if (moduleConfig == null) {
            return false;
        }
        String selectedModule = settings.getSelectedModule();
        String url = moduleConfig.getUrl();
        String apiKey = moduleConfig.getApiKey();
        if (com.yohannzhang.aigit.constant.Constants.VLLM.equals(client)) {
            return StringUtils.isNotEmpty(selectedModule) && StringUtils.isNotEmpty(url);
        }
        return StringUtils.isNotEmpty(selectedModule) && StringUtils.isNotEmpty(url) && StringUtils.isNotEmpty(apiKey);
    }

    public static @NotNull HttpURLConnection getHttpURLConnection(String url, String module, String apiKey, String textContent) throws IOException {
        OpenAIRequestBO openAIRequestBO = new OpenAIRequestBO();
        openAIRequestBO.setModel(module);
        openAIRequestBO.setStream(true);
        openAIRequestBO.setMessages(List.of(new OpenAIRequestBO.OpenAIRequestMessage("user", textContent)));

        ObjectMapper objectMapper1 = new ObjectMapper();
        String jsonInputString = objectMapper1.writeValueAsString(openAIRequestBO);

        URI uri = URI.create(url);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept-Charset", "UTF-8");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000); // 连接超时：30秒
        connection.setReadTimeout(30000); // 读取超时：30秒

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection;
    }

    public static @NotNull HttpURLConnection getVllmMessagesHttpURLConnection(String url, String module, String apiKey,
                                                                               String textContent, int maxTokens) throws IOException {
        Map<String, Object> request = Map.of(
                "model", module,
                "max_tokens", maxTokens,
                "messages", List.of(new OpenAIRequestBO.OpenAIRequestMessage("user", textContent))
        );

        ObjectMapper mapper = new ObjectMapper();
        String jsonInputString = mapper.writeValueAsString(request);

        URI uri = URI.create(normalizeVllmMessagesUrl(url));
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept-Charset", "UTF-8");
        if (StringUtils.isNotEmpty(apiKey)) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection;
    }

    public static void cancelRequest() {
        isCancelled = true;
    }

    public static void getAIResponseStream(String client, String textContent, Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) throws Exception {
        if (com.yohannzhang.aigit.constant.Constants.VLLM.equals(client)) {
            getVllmResponseStream(textContent, onNext, onError, onComplete);
            return;
        }

        isCancelled = false;

        ApiKeySettings settings = ApiKeySettings.getInstance();
        String selectedModule = settings.getSelectedModule();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(client);

        HttpURLConnection connection = OpenAIUtil.getHttpURLConnection(moduleConfig.getUrl(), selectedModule,
                moduleConfig.getApiKey(), textContent);

        String charset = getCharsetFromContentType(connection.getContentType());

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null && !isCancelled) {
                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6);
                        if (!"[DONE]".equals(jsonData)) {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode root = mapper.readTree(jsonData);
                            JsonNode choices = root.path("choices");
                            if (choices.isArray() && !choices.isEmpty()) {
                                String text = choices.get(0).path("delta").path("content").asText();
                                onNext.accept(text);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (!isCancelled) {
                    onError.accept(e);
                }
            } finally {
                // 流结束时触发 onComplete
                onComplete.run();
                connection.disconnect(); // 确保连接被释放
            }
        }).start();
    }

    public static void getVllmResponseStream(String textContent, Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) throws Exception {
        isCancelled = false;

        ApiKeySettings settings = ApiKeySettings.getInstance();
        String selectedModule = settings.getSelectedModule();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(com.yohannzhang.aigit.constant.Constants.VLLM);

        HttpURLConnection connection = getVllmMessagesHttpURLConnection(
                moduleConfig.getUrl(),
                selectedModule,
                moduleConfig.getApiKey(),
                textContent,
                8192
        );

        new Thread(() -> {
            try {
                int responseCode = connection.getResponseCode();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                        getCharsetFromContentType(connection.getContentType())))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null && !isCancelled) {
                        response.append(line);
                    }

                    if (responseCode >= 400) {
                        onError.accept(new IOException("vLLM API request failed, status=" + responseCode + ", body=" + response));
                        return;
                    }

                    onNext.accept(parseVllmMessagesResponse(response.toString()));
                }
            } catch (IOException e) {
                if (!isCancelled) {
                    onError.accept(e);
                }
            } finally {
                onComplete.run();
                connection.disconnect();
            }
        }).start();
    }

    public static String getVllmResponse(String textContent) throws Exception {
        ApiKeySettings settings = ApiKeySettings.getInstance();
        String selectedModule = settings.getSelectedModule();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(com.yohannzhang.aigit.constant.Constants.VLLM);
        HttpURLConnection connection = getVllmMessagesHttpURLConnection(
                moduleConfig.getUrl(),
                selectedModule,
                moduleConfig.getApiKey(),
                textContent,
                8192
        );
        try {
            int responseCode = connection.getResponseCode();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                    getCharsetFromContentType(connection.getContentType())))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                if (responseCode >= 400) {
                    throw new IOException("vLLM API request failed, status=" + responseCode + ", body=" + response);
                }
                return parseVllmMessagesResponse(response.toString());
            }
        } finally {
            connection.disconnect();
        }
    }

    public static String parseVllmMessagesResponse(String responseBody) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseBody);
        JsonNode content = root.path("content");
        StringBuilder text = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode block : content) {
                String value = block.path("text").asText("");
                if (StringUtils.isNotEmpty(value)) {
                    text.append(value);
                }
            }
        } else if (content.has("text")) {
            text.append(content.path("text").asText(""));
        } else {
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                text.append(choices.get(0).path("message").path("content").asText(""));
            }
        }
        return cleanThinkingContent(text.toString());
    }

    private static String cleanThinkingContent(String text) {
        String cleanText = Pattern.compile("<thinking>.*?</thinking>", Pattern.DOTALL).matcher(text).replaceAll("");
        cleanText = cleanText.replaceAll("</think>\\s*|</thinking>\\s*", "");
        cleanText = cleanText.replaceAll("(?m)^</think>|^</thinking>", "");
        return cleanText.trim();
    }

    private static String normalizeVllmMessagesUrl(String url) {
        String normalizedUrl = StringUtils.removeEnd(url, "/");
        if (normalizedUrl.endsWith("/v1/messages") || normalizedUrl.endsWith("/v1/chat/completions")) {
            return normalizedUrl;
        }
        return normalizedUrl + "/v1/messages";
    }


    private static String getCharsetFromContentType(String contentType) {
        if (contentType != null) {
            String[] values = contentType.split(";");
            for (String value : values) {
                value = value.trim();
                if (value.toLowerCase().startsWith("charset=")) {
                    return value.substring("charset=".length());
                }
            }
        }
        return StandardCharsets.UTF_8.name(); // 默认使用UTF-8
    }


}
