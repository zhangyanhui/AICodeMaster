package com.yohannzhang.aigit.core.models;

import java.util.Map;
import java.util.HashMap;

public class ProjectMetadata {
    private final String path;
    private Map<String, FileMetadata> files;
    private Map<String, Object> metrics;
    private Map<String, Object> analysisResults;

    public ProjectMetadata(String path) {
        this.path = path;
        this.files = new HashMap<>();
        this.metrics = new HashMap<>();
        this.analysisResults = new HashMap<>();
    }

    public String getPath() {
        return path;
    }

    public Map<String, FileMetadata> getFiles() {
        return files;
    }

    public void setFiles(Map<String, FileMetadata> files) {
        this.files = files;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }

    public Map<String, Object> getAnalysisResults() {
        return analysisResults;
    }

    public void setAnalysisResults(Map<String, Object> analysisResults) {
        this.analysisResults = analysisResults;
    }

    @Override
    public String toString() {
        return String.format("ProjectMetadata{path='%s', files=%d, metrics=%d, analysisResults=%d}",
                           path, files.size(), metrics.size(), analysisResults.size());
    }
} 