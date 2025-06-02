package com.yohannzhang.aigit.core.models;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

public class Project {
    private final String id;
    private final String name;
    private final Path rootPath;
    private final LocalDateTime lastAnalyzed;
    private final Map<String, FileMetadata> files;
    private final Map<String, Object> stats;
    private final Map<String, Object> analysisResults;

    public Project(String id, String name, Path rootPath) {
        this.id = id;
        this.name = name;
        this.rootPath = rootPath;
        this.lastAnalyzed = LocalDateTime.now();
        this.files = new HashMap<>();
        this.stats = new HashMap<>();
        this.analysisResults = new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public LocalDateTime getLastAnalyzed() {
        return lastAnalyzed;
    }

    public Map<String, FileMetadata> getFiles() {
        return Collections.unmodifiableMap(files);
    }

    public void addFile(FileMetadata file) {
        files.put(file.getPath(), file);
    }

    public Map<String, Object> getStats() {
        return Collections.unmodifiableMap(stats);
    }

    public void addStat(String key, Object value) {
        stats.put(key, value);
    }

    public Map<String, Object> getAnalysisResults() {
        return Collections.unmodifiableMap(analysisResults);
    }

    public void addAnalysisResult(String key, Object value) {
        analysisResults.put(key, value);
    }

    @Override
    public String toString() {
        return String.format("Project{id='%s', name='%s', rootPath='%s', lastAnalyzed=%s, files=%d}",
                id, name, rootPath, lastAnalyzed, files.size());
    }
} 