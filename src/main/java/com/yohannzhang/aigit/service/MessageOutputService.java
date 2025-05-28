package com.yohannzhang.aigit.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.jcef.JBCefBrowser;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MessageOutputService {
    private static final Parser parser;
    private static final HtmlRenderer renderer;
    private final JBCefBrowser browser;
    private final Color backgroundColor;
    private final Color fontColor;
    private final float fontSize;

    static {
        MutableDataSet options = new MutableDataSet();
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    public MessageOutputService(JBCefBrowser browser, Color backgroundColor, Color fontColor, float fontSize) {
        this.browser = browser;
        this.backgroundColor = backgroundColor;
        this.fontColor = fontColor;
        this.fontSize = fontSize;
    }

    public void updateResult(String markdownResult) {
        if (browser == null) return;

        // Add spacing between code blocks and HTML elements
        String processedMarkdown = markdownResult
                .replace("```\n</div>", "```\n\n</div>")
                .replace("```\n    </div>", "```\n\n    </div>");

        // 解析 Markdown 到 HTML
        com.vladsch.flexmark.util.ast.Document document = parser.parse(processedMarkdown);
        String htmlBody = renderer.render(document);

        // 转义反引号防止 JS 注入问题
        String safeHtml = htmlBody.replace("`", "\\`");

        // 使用 JS 更新内容并触发高亮和滚动
        String script = String.format(
                "document.getElementById('content').innerHTML = `%s`; " +
                        "document.querySelectorAll('pre code').forEach((block) => { hljs.highlightElement(block); }); " +
                        "addCopyButtons(); " +
                        "window.scrollTo(0, document.body.scrollHeight);",
                safeHtml
        );

        ApplicationManager.getApplication().invokeLater(() -> {
            browser.getCefBrowser().executeJavaScript(script, "about:blank", 0);
        });
    }

    public void showError(String message) {
        String errorHtml = String.format(
                "<div style='color: #ff6b6b; padding: 10px; background-color: rgba(255, 107, 107, 0.1); border-radius: 4px;'>%s</div>",
                message
        );
        updateResult(errorHtml);
    }

    public void showWelcomePage(String welcomeHtml) {
        String script = String.format(
                "document.documentElement.style.setProperty('--font-size', '%s');" +
                        "document.documentElement.style.setProperty('--workspace-color', '%s');" +
                        "document.documentElement.style.setProperty('--idefont-color', '%s');",
                fontSize + "px",
                toHex(backgroundColor),
                toHex(fontColor)
        );

        ApplicationManager.getApplication().invokeLater(() -> {
            browser.loadHTML(welcomeHtml);
            browser.getCefBrowser().executeJavaScript(script, "about:blank", 0);
        });
    }

    public String formatQuestion(String question) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return String.format(
                "<div class='chat-item question-item' style='margin: 10px 0; padding: 10px; border-left: 3px solid #4CAF50; position: relative;'>" +
                        "<div style='display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px;'>" +
                        "<strong style='color: #4CAF50;'>Q:</strong>" +
                        "<div style='display: flex; align-items: center; gap: 10px;'>" +
                        "<span style='color: #666; font-size: 0.9em;'>%s</span>" +
                        "<button class='delete-btn' style='background: none; border: none; color: #999; cursor: pointer; font-size: 14px; padding: 2px 6px; border-radius: 3px; transition: all 0.2s;' onclick='deleteQuestion(this)'>×</button>" +
                        "</div>" +
                        "</div>" +
                        "<div style='margin-left: 20px;'>%s</div>" +
                        "</div>",
                timestamp, question
        );
    }

    public String formatAnswer(String answer) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return String.format(
                "<div class='chat-item answer-item' style='margin: 10px 0; padding: 10px; border-left: 3px solid #2196F3;'>" +
                        "<div style='display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px;'>" +
                        "<strong style='color: #2196F3;'>A:</strong>" +
                        "<span style='color: #666; font-size: 0.9em;'>%s</span>" +
                        "</div>" +
                        "<div style='margin-left: 20px;'>%s</div>" +
                        "</div>",
                timestamp, answer
        );
    }

    public String formatHistoryItem(String question, String answer) {
        return "<div class='history-item'>" +
                "<div class='question'>" + question + "</div>" +
                "<div class='answer'>" + answer + "</div>" +
                "</div>";
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
} 