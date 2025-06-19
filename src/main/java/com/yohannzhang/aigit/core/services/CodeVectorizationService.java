package com.yohannzhang.aigit.core.services;

import com.intellij.openapi.project.Project;
import com.yohannzhang.aigit.core.models.FileMetadata;
import com.yohannzhang.aigit.core.models.ProjectMetadata;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CodeVectorizationService {
    private final Map<String, List<Float>> codeVectors = new ConcurrentHashMap<>();
    private final Map<String, String> codeChunks = new ConcurrentHashMap<>();
    private static final int CHUNK_SIZE = 1000; // 每个代码块的最大字符数
    private static final int OVERLAP_SIZE = 200; // 代码块之间的重叠字符数

    public void vectorizeProject(ProjectMetadata project) {
        project.getFiles().values().parallelStream().forEach(this::vectorizeFile);
    }

    private void vectorizeFile(FileMetadata file) {
        String content = file.getContent();
        if (content == null || content.isEmpty()) {
            return;
        }

        // 将文件内容分割成重叠的代码块
        List<String> chunks = splitIntoChunks(content);
        
        // 为每个代码块生成向量
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String chunkId = file.getPath() + "#" + i;
            codeChunks.put(chunkId, chunk);
            
            // 使用简单的向量化方法（这里可以替换为实际的向量化模型）
            List<Float> vector = generateVector(chunk);
            codeVectors.put(chunkId, vector);
        }
    }

    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        
        while (start < content.length()) {
            int end = Math.min(start + CHUNK_SIZE, content.length());
            
            // 如果不是文件末尾，尝试在合适的位置分割
            if (end < content.length()) {
                // 尝试在换行符处分割
                int lastNewline = content.lastIndexOf('\n', end);
                if (lastNewline > start) {
                    end = lastNewline;
                }
            }
            
            chunks.add(content.substring(start, end));
            start = end - OVERLAP_SIZE;
        }
        
        return chunks;
    }

    private List<Float> generateVector(String text) {
        // 这里使用一个简单的向量化方法，实际应用中应该使用专业的向量化模型
        // 例如：OpenAI的text-embedding-ada-002、HuggingFace的sentence-transformers等
        List<Float> vector = new ArrayList<>();
        String[] words = text.toLowerCase().split("\\s+");
        Map<String, Integer> wordFreq = new HashMap<>();
        
        // 计算词频
        for (String word : words) {
            wordFreq.merge(word, 1, Integer::sum);
        }
        
        // 生成简单的词频向量
        for (String word : wordFreq.keySet()) {
            vector.add((float) wordFreq.get(word) / words.length);
        }
        
        // 确保向量维度一致
        while (vector.size() < 384) { // 使用384维向量
            vector.add(0.0f);
        }
        if (vector.size() > 384) {
            vector = vector.subList(0, 384);
        }
        
        return vector;
    }

    public List<String> searchSimilarCode(String query, int topK) {
        List<Float> queryVector = generateVector(query);
        
        // 计算相似度并排序
        return codeVectors.entrySet().parallelStream()
            .map(entry -> new AbstractMap.SimpleEntry<>(
                entry.getKey(),
                cosineSimilarity(queryVector, entry.getValue())
            ))
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private double cosineSimilarity(List<Float> v1, List<Float> v2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    public String getCodeChunk(String chunkId) {
        return codeChunks.get(chunkId);
    }

    public void clear() {
        codeVectors.clear();
        codeChunks.clear();
    }
} 