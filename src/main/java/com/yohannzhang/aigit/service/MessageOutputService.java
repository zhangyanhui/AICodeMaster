package com.yohannzhang.aigit.service;

import com.intellij.openapi.application.ApplicationManager;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MessageOutputService {
    private static final Parser parser;
    private static final HtmlRenderer renderer;
    private final JEditorPane editorPane;
    private final Color backgroundColor;
    private final Color fontColor;
    private final float fontSize;

    static {
        MutableDataSet options = new MutableDataSet();
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    public MessageOutputService(JEditorPane editorPane, Color backgroundColor, Color fontColor, float fontSize) {
        this.editorPane = editorPane;
        this.backgroundColor = backgroundColor;
        this.fontColor = fontColor;
        this.fontSize = fontSize;
    }

    public void updateResult(String markdownResult) {
        if (editorPane == null) return;

        // 解析 Markdown 到 HTML
        com.vladsch.flexmark.util.ast.Document document = parser.parse(markdownResult);
        String htmlBody = renderer.render(document);

        // 添加基本样式
        String styledHtml = String.format(
            "<html><head><style>" +
            "body { background-color: %s; color: %s; font-size: %s; }" +
            "pre { background-color: #f5f5f5; padding: 10px; border-radius: 4px; }" +
            "code { font-family: monospace; }" +
            "</style></head><body>%s</body></html>",
            toHex(backgroundColor),
            toHex(fontColor),
            fontSize + "px",
            htmlBody
        );

        ApplicationManager.getApplication().invokeLater(() -> {
            editorPane.setText(styledHtml);
            editorPane.setCaretPosition(0);
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
        String styledHtml = String.format(
            "<html><head><style>" +
            "body { background-color: %s; color: %s; font-size: %s; }" +
            "</style></head><body>%s</body></html>",
            toHex(backgroundColor),
            toHex(fontColor),
            fontSize + "px",
            welcomeHtml
        );

        ApplicationManager.getApplication().invokeLater(() -> {
            editorPane.setText(styledHtml);
            editorPane.setCaretPosition(0);
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