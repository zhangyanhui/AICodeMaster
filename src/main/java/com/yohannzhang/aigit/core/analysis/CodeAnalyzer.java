package com.yohannzhang.aigit.core.analysis;

import com.yohannzhang.aigit.core.models.FileMetadata;
import com.yohannzhang.aigit.core.models.Project;
import com.yohannzhang.aigit.core.models.Symbol;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;

public interface CodeAnalyzer {
    /**
     * 分析整个项目
     * @param projectPath 项目根目录路径
     * @return 项目分析结果
     */
    Project analyzeProject(Path projectPath);

    /**
     * 分析单个文件
     * @param filePath 文件路径
     * @return 文件元数据
     * @throws IOException 如果文件读取失败
     */
    FileMetadata analyzeFile(Path filePath) throws IOException;

    /**
     * 提取代码中的符号（类、方法、变量等）
     * @param content 文件内容
     * @param language 编程语言
     * @return 符号映射表，key为符号类型，value为该类型的符号列表
     */
    Map<String, List<Symbol>> extractSymbols(String content, String language);

    /**
     * 提取代码中的依赖关系
     * @param content 文件内容
     * @param language 编程语言
     * @return 依赖列表
     */
    List<String> extractDependencies(String content, String language);

    /**
     * 生成代码摘要
     * @param content 文件内容
     * @param language 编程语言
     * @return 代码摘要
     */
    String generateSummary(String content, String language);

    /**
     * 分析代码质量
     * @param content 文件内容
     * @param language 编程语言
     * @return 代码质量指标
     */
    Map<String, Object> analyzeCodeQuality(String content, String language);

    /**
     * 分析代码复杂度
     * @param content 文件内容
     * @param language 编程语言
     * @return 复杂度指标
     */
    Map<String, Object> analyzeComplexity(String content, String language);

    /**
     * 分析重复代码
     * @param project 项目对象
     * @return 重复代码片段列表
     */
    List<Map<String, Object>> analyzeDuplication(Project project);

    /**
     * 分析依赖关系
     * @param project 项目对象
     * @return 依赖关系图
     */
    Map<String, List<String>> analyzeDependencies(Project project);

    /**
     * 分析代码结构
     * @param code 文件内容
     * @param language 编程语言
     * @return 代码结构指标
     */
    Map<String, Object> analyzeCodeStructure(String code, String language);
} 