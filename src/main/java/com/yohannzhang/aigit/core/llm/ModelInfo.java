package com.yohannzhang.aigit.core.llm;

import java.util.HashMap;
import java.util.Map;

public class ModelInfo {
    private final String name;
    private final String version;
    private final String provider;
    private final int maxTokens;
    private final boolean isAvailable;
    private final Map<String, Object> capabilities;

    public ModelInfo(String name, String version, String provider, int maxTokens, boolean isAvailable) {
        this.name = name;
        this.version = version;
        this.provider = provider;
        this.maxTokens = maxTokens;
        this.isAvailable = isAvailable;
        this.capabilities = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getProvider() {
        return provider;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public void addCapability(String name, Object value) {
        capabilities.put(name, value);
    }

    @Override
    public String toString() {
        return String.format("ModelInfo{name='%s', version='%s', provider='%s', " +
                           "maxTokens=%d, isAvailable=%s, capabilities=%s}",
                           name, version, provider, maxTokens, isAvailable, capabilities);
    }
} 