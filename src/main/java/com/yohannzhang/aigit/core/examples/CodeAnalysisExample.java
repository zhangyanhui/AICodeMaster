package com.yohannzhang.aigit.core.examples;

import com.yohannzhang.aigit.core.actions.CodeAnalysisAction;
import com.yohannzhang.aigit.core.models.ActionResult;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class CodeAnalysisExample {
    public static void main(String[] args) {
        // 创建代码分析Action
        CodeAnalysisAction analysisAction = new CodeAnalysisAction(Paths.get("."));
        
        // 执行分析
        ActionResult result = analysisAction.execute();
        
        if (result.isSuccess()) {
            System.out.println("代码分析成功完成！");
            System.out.println("分析时间: " + result.getData());
            
            // 打印分析结果
            Map<String, Object> data = (Map<String, Object>) result.getData();
            Map<String, Object> qualityReport = (Map<String, Object>) data.get("qualityReport");
            Map<String, Object> summary = (Map<String, Object>) qualityReport.get("summary");
            
            System.out.println("\n=== 项目概览 ===");
            System.out.println("总文件数: " + summary.get("totalFiles"));
            System.out.println("总代码行数: " + summary.get("totalLines"));
            System.out.println("注释行数: " + summary.get("totalCommentLines"));
            System.out.println("注释比例: " + summary.get("commentRatio"));
            System.out.println("平均圈复杂度: " + summary.get("avgComplexity"));
            System.out.println("代码质量评级: " + summary.get("qualityRating"));
            
            System.out.println("\n=== 改进建议 ===");
            List<String> recommendations = (List<String>) summary.get("recommendations");
            for (String recommendation : recommendations) {
                System.out.println("- " + recommendation);
            }
        } else {
            System.out.println("代码分析失败: " + result.getMessage());
        }
    }
} 