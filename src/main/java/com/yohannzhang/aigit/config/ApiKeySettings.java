package com.yohannzhang.aigit.config;

import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.pojo.PromptInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(name = "com.yohannzhang.aigit.config.ApiKeySettings", storages = { @Storage("AICodeMasterettings.xml") })
public class ApiKeySettings implements PersistentStateComponent<ApiKeySettings> {
    private String selectedClient = "Gemini";
    private String selectedModule = "gemini-2.0-flash-exp";
    private String commitLanguage = "English";

    private String promptType = Constants.CUSTOM_PROMPT;

    // prompt from table
    private List<PromptInfo> customPrompts = new ArrayList<>();

    // current prompt by user choose
    private PromptInfo customPrompt = new PromptInfo("", "");

    private Map<String, ModuleConfig> moduleConfigs = new HashMap<>();

    public static ApiKeySettings getInstance() {
        return ApplicationManager.getApplication().getService(ApiKeySettings.class);
    }
    //获取apikey不为空的模型列表,按以下格式返回 new String[]{}
    public String[] getAvailableModels() {
        String[] result = new String[moduleConfigs.size()];
        int i = 0;
        for (Map.Entry<String, ModuleConfig> entry : moduleConfigs.entrySet()) {
            ModuleConfig config = entry.getValue();
            if (config != null && config.getApiKey() != null && !config.getApiKey().isEmpty()) {
                result[i] = entry.getKey();
                i++;
            }
        }
        return result;
    }




    @Nullable
    @Override
    public ApiKeySettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ApiKeySettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String getSelectedClient() {
        return selectedClient;
    }

    public void setSelectedClient(String selectedClient) {
        this.selectedClient = selectedClient;
    }

    public String getCommitLanguage() {
        return commitLanguage;
    }

    public void setCommitLanguage(String commitLanguage) {
        this.commitLanguage = commitLanguage;
    }

    public List<PromptInfo> getCustomPrompts() {
        if (customPrompts == null || customPrompts.isEmpty()) {
            customPrompts = PromptInfo.defaultPrompts();
        }
        return customPrompts;
    }

    public void setCustomPrompts(List<PromptInfo> customPrompts) {
        this.customPrompts = customPrompts;
    }

    public PromptInfo getCustomPrompt() {
        return customPrompt;
    }

    public void setCustomPrompt(PromptInfo customPrompt) {
        this.customPrompt = customPrompt;
    }

    public String getPromptType() {
        return promptType;
    }

    public void setPromptType(String promptType) {
        this.promptType = promptType;
    }

    public String getSelectedModule() {
        return selectedModule;
    }

    public void setSelectedModule(String selectedModule) {
        this.selectedModule = selectedModule;
    }

    public Map<String, ModuleConfig> getModuleConfigs() {
        return moduleConfigs;
    }

    public void setModuleConfigs(Map<String, ModuleConfig> moduleConfigs) {
        this.moduleConfigs = moduleConfigs;
    }

    public static class ModuleConfig {
        private String url;
        private String apiKey;

        public ModuleConfig() {
        }

        public ModuleConfig(String url, String apiKey) {
            this.url = url;
            this.apiKey = apiKey;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}