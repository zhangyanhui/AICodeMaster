package com.yohannzhang.aigit.core.services;

import com.yohannzhang.aigit.core.models.ProjectMetadata;
import com.yohannzhang.aigit.core.models.FileMetadata;

import java.util.*;
import java.util.stream.Collectors;

public class CodeRAGService {
    private final CodeVectorizationService vectorizationService;
    private ProjectMetadata currentProject;
    private static final int DEFAULT_TOP_K = 5;

    public CodeRAGService() {
        this.vectorizationService = new CodeVectorizationService();
    }

    public void initializeProject(ProjectMetadata project) {
        this.currentProject = project;
        vectorizationService.vectorizeProject(project);
    }

    public List<CodeSearchResult> searchCode(String query) {
        return searchCode(query, DEFAULT_TOP_K);
    }

    public List<CodeSearchResult> searchCode(String query, int topK) {
        if (currentProject == null) {
            throw new IllegalStateException("Project not initialized");
        }

        List<String> similarChunkIds = vectorizationService.searchSimilarCode(query, topK);
        return similarChunkIds.stream()
            .map(chunkId -> {
                String[] parts = chunkId.split("#");
                String filePath = parts[0];
                int chunkIndex = Integer.parseInt(parts[1]);
                String codeChunk = vectorizationService.getCodeChunk(chunkId);
                
                FileMetadata fileMetadata = currentProject.getFiles().get(filePath);
                return new CodeSearchResult(
                    filePath,
                    fileMetadata != null ? fileMetadata.getLanguage() : "Unknown",
                    codeChunk,
                    chunkIndex
                );
            })
            .collect(Collectors.toList());
    }

    public String generateContext(String query) {
        List<CodeSearchResult> results = searchCode(query);
        if (results.isEmpty()) {
            return "No relevant code found.";
        }

        StringBuilder context = new StringBuilder();
        context.append("Relevant code snippets:\n\n");
        
        for (CodeSearchResult result : results) {
            context.append("File: ").append(result.filePath())
                  .append(" (Language: ").append(result.language()).append(")\n")
                  .append("```\n")
                  .append(result.codeChunk())
                  .append("\n```\n\n");
        }

        return context.toString();
    }

    public void clear() {
        vectorizationService.clear();
        currentProject = null;
    }

    public static class CodeSearchResult {
        private final String filePath;
        private final String language;
        private final String codeChunk;
        private final int chunkIndex;

        public CodeSearchResult(String filePath, String language, String codeChunk, int chunkIndex) {
            this.filePath = filePath;
            this.language = language;
            this.codeChunk = codeChunk;
            this.chunkIndex = chunkIndex;
        }

        public String filePath() {
            return filePath;
        }

        public String language() {
            return language;
        }

        public String codeChunk() {
            return codeChunk;
        }

        public int chunkIndex() {
            return chunkIndex;
        }
    }
} 