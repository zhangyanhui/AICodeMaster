package com.yohannzhang.aigit.core.models;

import java.time.LocalDateTime;
import java.util.*;

public class FileMetadata {
    private final String path;
    private final String language;
    private final long size;
    private final int lines;
    private final LocalDateTime lastModified;
    private final String contentHash;
    private final Map<String, List<Symbol>> symbols;
    private final List<String> dependencies;
    private final String summary;
    private final Map<String, Object> metrics;
    private final Map<String, Object> analysisResults;

    public FileMetadata(String path, String language, long size, int lines, 
                       LocalDateTime lastModified, String contentHash,
                       Map<String, List<Symbol>> symbols, List<String> dependencies,
                       String summary) {
        this.path = path;
        this.language = language;
        this.size = size;
        this.lines = lines;
        this.lastModified = lastModified;
        this.contentHash = contentHash;
        this.symbols = new HashMap<>(symbols);
        this.dependencies = new ArrayList<>(dependencies);
        this.summary = summary;
        this.metrics = new HashMap<>();
        this.analysisResults = new HashMap<>();
    }

    public String getPath() {
        return path;
    }

    public String getLanguage() {
        return language;
    }

    public long getSize() {
        return size;
    }

    public int getLines() {
        return lines;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Map<String, List<Symbol>> getSymbols() {
        return Collections.unmodifiableMap(symbols);
    }

    public List<String> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getMetrics() {
        return Collections.unmodifiableMap(metrics);
    }

    public void addMetric(String key, Object value) {
        metrics.put(key, value);
    }

    public Map<String, Object> getAnalysisResults() {
        return Collections.unmodifiableMap(analysisResults);
    }

    public void addAnalysisResult(String key, Object value) {
        analysisResults.put(key, value);
    }

    @Override
    public String toString() {
        return String.format("FileMetadata{path='%s', language='%s', size=%d, lines=%d, " +
                           "lastModified=%s, contentHash='%s', symbols=%d, dependencies=%d}",
                           path, language, size, lines, lastModified, contentHash,
                           symbols.size(), dependencies.size());
    }
} 