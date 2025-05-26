package com.yohannzhang.aigit.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HtmlTemplateService {
    private static final String WELCOME_TEMPLATE = "welcome.html";
    private static final String BASE_TEMPLATE = "base.html";

    public String getWelcomeTemplate() {
        return readResourceFile(WELCOME_TEMPLATE);
    }

    public String getBaseTemplate() {
        return readResourceFile(BASE_TEMPLATE);
    }

    public String getBaseHtmlWithStyles() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/styles/github.min.css'>\n" +
                "    <script src='https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/highlight.min.js'></script>\n" +
                "    <script src='https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/languages/java.min.js'></script>\n" +
                "    <script src='https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/languages/xml.min.js'></script>\n" +
                "    <script src='https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/languages/javascript.min.js'></script>\n" +
                "    <style>\n" +
                "        body { margin: 0; padding: 20px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }\n" +
                "        #content { color: #666; font-size: 14px; line-height: 1.6; }\n" +
                "        pre { background-color: #f6f8fa; border-radius: 6px; padding: 16px; }\n" +
                "        code { font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace; }\n" +
                "        .welcome-container { max-width: 800px; margin: 0 auto; padding: 40px 20px; }\n" +
                "        .welcome-header { text-align: center; margin-bottom: 40px; }\n" +
                "        .welcome-title { color: #2c3e50; font-size: 28px; margin-bottom: 10px; font-weight: 600; }\n" +
                "        .welcome-subtitle { color: #7f8c8d; font-size: 16px; margin-bottom: 30px; }\n" +
                "        .feature-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; margin-bottom: 40px; }\n" +
                "        .feature-card { background: #fff; border-radius: 8px; padding: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); transition: transform 0.2s; }\n" +
                "        .feature-card:hover { transform: translateY(-2px); }\n" +
                "        .feature-icon { font-size: 24px; margin-bottom: 15px; color: #3498db; }\n" +
                "        .feature-title { color: #2c3e50; font-size: 18px; font-weight: 500; margin-bottom: 10px; }\n" +
                "        .feature-desc { color: #7f8c8d; font-size: 14px; line-height: 1.5; }\n" +
                "        .quick-start { background: #f8f9fa; border-radius: 8px; padding: 20px; margin-top: 30px; }\n" +
                "        .quick-start-title { color: #2c3e50; font-size: 18px; font-weight: 500; margin-bottom: 15px; }\n" +
                "        .quick-start-list { list-style: none; padding: 0; margin: 0; }\n" +
                "        .quick-start-item { display: flex; align-items: center; margin-bottom: 12px; color: #7f8c8d; }\n" +
                "        .quick-start-item:before { content: '•'; color: #3498db; font-size: 20px; margin-right: 10px; }\n" +
                "        .welcome-footer { text-align: center; margin-top: 40px; color: #95a5a6; font-size: 13px; }\n" +
                "        .delete-btn:hover { color: #ff6b6b !important; background-color: rgba(255, 107, 107, 0.1); }\n" +
                "        .chat-item { position: relative; }\n" +
                "        .chat-item:hover .delete-btn { opacity: 1; }\n" +
                "        .delete-btn { opacity: 0; transition: opacity 0.2s, color 0.2s, background-color 0.2s; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id='content'></div>\n" +
                "    <script>\n" +
                "        function addCopyButtons() {\n" +
                "            document.querySelectorAll('pre code').forEach((block) => {\n" +
                "                const button = document.createElement('button');\n" +
                "                button.className = 'copy-button';\n" +
                "                button.textContent = '复制';\n" +
                "                button.style.cssText = 'position: absolute; top: 5px; right: 5px; padding: 5px 10px; background: #e1e4e8; border: none; border-radius: 3px; cursor: pointer;';\n" +
                "                block.parentNode.style.position = 'relative';\n" +
                "                block.parentNode.appendChild(button);\n" +
                "                button.addEventListener('click', () => {\n" +
                "                    navigator.clipboard.writeText(block.textContent);\n" +
                "                    button.textContent = '已复制';\n" +
                "                    setTimeout(() => button.textContent = '复制', 2000);\n" +
                "                });\n" +
                "            });\n" +
                "        }\n" +
                "        function deleteQuestion(btn) {\n" +
                "            const questionDiv = btn.closest('.question-item');\n" +
                "            const nextHr = questionDiv.nextElementSibling;\n" +
                "            const answerDiv = nextHr ? nextHr.nextElementSibling : null;\n" +
                "            const nextHr2 = answerDiv ? answerDiv.nextElementSibling : null;\n" +
                "            \n" +
                "            if (questionDiv) questionDiv.remove();\n" +
                "            if (nextHr) nextHr.remove();\n" +
                "            if (answerDiv) answerDiv.remove();\n" +
                "            if (nextHr2) nextHr2.remove();\n" +
                "            \n" +
                "            if (document.querySelectorAll('.chat-item').length === 0) {\n" +
                "                document.getElementById('content').innerHTML = document.querySelector('.welcome-container').outerHTML;\n" +
                "            }\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    private String readResourceFile(String filename) {
        StringBuilder content = new StringBuilder();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename);
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
} 