package com.yohannzhang.aigit.core.models;

import java.util.Map;

public interface Plugin {
    String getId();
    String getName();
    String getVersion();
    String getDescription();
    void initialize(PluginContext context);
} 