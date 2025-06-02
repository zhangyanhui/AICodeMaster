package com.yohannzhang.aigit.util;

import com.intellij.openapi.project.Project;
import com.yohannzhang.aigit.config.ApiKeySettings;

public class ModuleConfigUtil {
    public static ApiKeySettings.ModuleConfig getModuleConfig(Project project) {
        ApiKeySettings settings = ApiKeySettings.getInstance();
        String selectedClient = settings.getSelectedClient();
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs().get(selectedClient);
        if (moduleConfig == null) {
            throw new IllegalStateException(selectedClient + " configuration not found");
        }
        return moduleConfig;
    }
} 