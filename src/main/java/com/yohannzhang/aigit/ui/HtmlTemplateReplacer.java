package com.yohannzhang.aigit.ui;

import java.io.*;
import java.awt.*;

public class HtmlTemplateReplacer {

    public static String replaceCssVariables(String templatePath, float fontSize, Color workspaceColor, Color ideFontColor)  {
        // 读取模板文件
        String content = readResourceFile(templatePath);

        // 替换占位符
        content = content.replace("--font-size: 14px", String.valueOf("--font-size: "+fontSize));
        content = content.replace("--workspace-color: #ffffff", "--workspace-color:"+toHex(workspaceColor));
        content = content.replace("--idefont-color: #2B2B2B", " --idefont-color:"+toHex(ideFontColor));

        return content;
    }


    private static String readResourceFile(String filename) {
        StringBuilder content = new StringBuilder();
        try (InputStream is = HtmlTemplateReplacer.class.getClassLoader().getResourceAsStream(filename);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            if (is == null) {
                throw new RuntimeException("Resource not found: " + filename);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource file: " + filename, e);
        }
        return content.toString();
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static void main(String[] args) {
        try {
            // 示例调用
            String updatedHtml = replaceCssVariables(
                    "empty.html",
                    16f,
                    new Color(30, 30, 30),
                    new Color(220, 220, 220)
            );

            System.out.println(updatedHtml); // 或者加载到浏览器中

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
