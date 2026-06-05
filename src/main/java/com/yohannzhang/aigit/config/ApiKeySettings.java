package com.yohannzhang.aigit.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.pojo.PromptInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@State(name = "com.yohannzhang.aigit.config.ApiKeySettings", storages = {@Storage("AICodeMasterettings.xml")})
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
    private Map<String, List<String>> customClientModules = new HashMap<>();

    public static ApiKeySettings getInstance() {
        return ApplicationManager.getApplication().getService(ApiKeySettings.class);
    }

    //获取apikey不为空的模型列表,按以下格式返回 new String[]{}
    public String[] getAvailableModels() {
        ensureDefaultModuleConfigs();

        Set<String> result = new LinkedHashSet<>();
        for (String client : Constants.LLM_CLIENTS) {
            if (moduleConfigs.containsKey(client)) {
                result.add(client);
            }
        }
        result.addAll(moduleConfigs.keySet());

        return result.toArray(new String[0]);
    }


    @Nullable
    @Override
    public ApiKeySettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ApiKeySettings state) {
        XmlSerializerUtil.copyBean(state, this);
        ensureDefaultModuleConfigs();
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
        ensureDefaultModuleConfigs();
        return moduleConfigs;
    }

    public void setModuleConfigs(Map<String, ModuleConfig> moduleConfigs) {
        this.moduleConfigs = moduleConfigs;
        ensureDefaultModuleConfigs();
    }

    public Map<String, List<String>> getCustomClientModules() {
        if (customClientModules == null) {
            customClientModules = new HashMap<>();
        }
        return customClientModules;
    }

    public void setCustomClientModules(Map<String, List<String>> customClientModules) {
        this.customClientModules = customClientModules;
    }

    public String[] getModulesForClient(String client) {
        List<String> modules = new ArrayList<>();
        String[] defaultModules = Constants.CLIENT_MODULES.get(client);
        if (defaultModules != null) {
            for (String module : defaultModules) {
                addIfAbsent(modules, module);
            }
        }
        List<String> customModules = getCustomClientModules().get(client);
        if (customModules != null) {
            for (String module : customModules) {
                addIfAbsent(modules, module);
            }
        }
        return modules.toArray(new String[0]);
    }

    public void addCustomModule(String client, String module) {
        if (client == null || module == null || module.trim().isEmpty()) {
            return;
        }
        String normalizedModule = module.trim();
        String[] defaultModules = Constants.CLIENT_MODULES.get(client);
        if (defaultModules != null) {
            for (String defaultModule : defaultModules) {
                if (normalizedModule.equals(defaultModule)) {
                    return;
                }
            }
        }

        List<String> modules = getCustomClientModules().computeIfAbsent(client, key -> new ArrayList<>());
        addIfAbsent(modules, normalizedModule);
    }

    private void addIfAbsent(List<String> modules, String module) {
        if (module != null && !module.trim().isEmpty() && !modules.contains(module.trim())) {
            modules.add(module.trim());
        }
    }

    private void ensureDefaultModuleConfigs() {
        if (moduleConfigs == null) {
            moduleConfigs = new HashMap<>();
        }
        for (Map.Entry<String, ModuleConfig> entry : Constants.moduleConfigs.entrySet()) {
            moduleConfigs.computeIfAbsent(entry.getKey(), key -> {
                ModuleConfig defaultConfig = entry.getValue();
                return new ModuleConfig(defaultConfig.getUrl(), defaultConfig.getApiKey());
            });
        }
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
