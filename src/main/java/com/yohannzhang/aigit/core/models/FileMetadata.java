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
    private Map<String, Object> metrics;
    private Map<String, Object> analysisResults;
    private String content;
    private String fileName;
    private int totalLines;
    private int commentLines;
    private int complexity;
    private List<Symbol> methods;
    private List<Symbol> classes;

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
        this.methods = new ArrayList<>();
        this.classes = new ArrayList<>();
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(int totalLines) {
        this.totalLines = totalLines;
    }

    public int getCommentLines() {
        return commentLines;
    }

    public void setCommentLines(int commentLines) {
        this.commentLines = commentLines;
    }

    public int getComplexity() {
        return complexity;
    }

    public void setComplexity(int complexity) {
        this.complexity = complexity;
    }

    public List<Symbol> getMethods() {
        return methods;
    }

    public void setMethods(List<Symbol> methods) {
        this.methods = methods;
    }

    public List<Symbol> getClasses() {
        return classes;
    }

    public void setClasses(List<Symbol> classes) {
        this.classes = classes;
    }

    @Override
    public String toString() {
        return String.format("FileMetadata{path='%s', language='%s', size=%d, lines=%d, " +
                           "lastModified=%s, contentHash='%s', symbols=%d, dependencies=%d}",
                           path, language, size, lines, lastModified, contentHash,
                           symbols.size(), dependencies.size());
    }
} 