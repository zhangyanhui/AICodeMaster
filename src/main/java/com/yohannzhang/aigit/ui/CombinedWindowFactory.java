package com.yohannzhang.aigit.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.jcef.JBCefBrowser;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class CombinedWindowFactory implements ToolWindowFactory {
    private static final CodeUtil CODE_UTIL = new CodeUtil();
    private static final Font BUTTON_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Color BUTTON_COLOR = new Color(0, 120, 215);
    private static final Color BUTTON_HOVER_COLOR = new Color(0, 100, 200);

    private JTextArea questionTextArea;
    private final StringBuilder messageBuilder = new StringBuilder();
    private  JBCefBrowser markdownViewer; // 使用 JBCefBrowser 替换 JEditorPane
    private  Color ideBackgroundColor; // 缓存 IDE 背景色

    // 初始化 Flexmark 配置（仅执行一次）
    private static final Parser parser;
    private static final HtmlRenderer renderer;




    static {
        MutableDataSet options = new MutableDataSet();
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }
    /**
     * 读取 classpath 中的资源文件并返回字符串
     */
    private static String readResourceFile(String filename) {
        StringBuilder content = new StringBuilder();
        try (InputStream is = CombinedWindowFactory.class.getClassLoader().getResourceAsStream(filename);
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

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 初始化 IDE 背景色
        initIdeBackgroundColor();

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultConstraints();

        panel.add(createOutputPanel(project), gbc);
        gbc.gridy = 1;
        gbc.weighty = 0.3;
        panel.add(createInputPanel(project), gbc);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    private void initIdeBackgroundColor() {
        EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
        ideBackgroundColor = colorsScheme.getDefaultBackground();
        Color ideFontColor = colorsScheme.getDefaultForeground();
        colorsScheme.getFontPreferences();
    }

    public void updateResult(String markdownResult) {
        if (markdownViewer == null) return;

        // 解析 Markdown 到 HTML
        com.vladsch.flexmark.util.ast.Document document = parser.parse(markdownResult);
        String htmlBody = renderer.render(document);

        // 转义反引号防止 JS 注入问题
        String safeHtml = htmlBody.replace("`", "\\`");

        // 使用 JS 更新内容并触发高亮和滚动
        String script = String.format(
                "document.getElementById('content').innerHTML = `%s`; hljs.highlightAll(); addCopyButtons(); window.scrollTo(0, document.body.scrollHeight);",
                safeHtml
        );

        ApplicationManager.getApplication().invokeLater(() -> {
            markdownViewer.getCefBrowser().executeJavaScript(script, "about:blank", 0);
        });
    }






    private GridBagConstraints createDefaultConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        return gbc;
    }

    private JPanel createOutputPanel(Project project) {
        JPanel outputPanel = new JPanel(new BorderLayout(10, 10));
        outputPanel.setBorder(BorderFactory.createTitledBorder("输出结果"));
        outputPanel.setBackground(ideBackgroundColor);

        markdownViewer = new JBCefBrowser();
        markdownViewer.getComponent().setBorder(BorderFactory.createEmptyBorder());
        String EMPTY_HTML = readResourceFile("empty.html");
        // 初始加载空 HTML 页面
        ApplicationManager.getApplication().invokeLater(() -> {
            markdownViewer.loadHTML(EMPTY_HTML);
        });

        outputPanel.add(markdownViewer.getComponent(), BorderLayout.CENTER);

        return outputPanel;
    }


    private JPanel createInputPanel(Project project) {
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("输入问题"));
        inputPanel.setBackground(ideBackgroundColor);

        questionTextArea = new JTextArea(5, 50);
        questionTextArea.setLineWrap(true);
        questionTextArea.setWrapStyleWord(true);
        questionTextArea.setBackground(ideBackgroundColor); // 输入框背景与 IDE 一致
        questionTextArea.setForeground(Color.WHITE); // 文本颜色可根据需要调整

        inputPanel.add(new JBScrollPane(questionTextArea), BorderLayout.CENTER);
        inputPanel.add(createButtonPanel(project), BorderLayout.SOUTH);
        AIGuiComponent.getInstance(project).setWindowFactory(this);

        return inputPanel;
    }

    private JPanel createButtonPanel(Project project) {
        JButton askButton = createStyledButton("提交问题");
        askButton.addActionListener(event -> handleAskButtonClick(project));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(ideBackgroundColor); // 按钮面板背景与 IDE 一致
        buttonPanel.add(askButton);
        return buttonPanel;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(BUTTON_FONT);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setBackground(BUTTON_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);

        // 鼠标悬停效果
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(BUTTON_HOVER_COLOR);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(BUTTON_COLOR);
            }
        });
        return button;
    }

    private void handleAskButtonClick(Project project) {
        String question = questionTextArea.getText().trim();
        if (question.isEmpty()) {
            updateResult("请输入问题！");
            return;
        }

        CodeService codeService = new CodeService();
        String formattedCode = CODE_UTIL.formatCode(question);
        String prompt = String.format("根据提出的问题作出回答，以Java作为默认编程语言输出，用中文回答，问题如下：%s", formattedCode);

        try {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "处理中", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    messageBuilder.setLength(0); // 重置内容构建器
                    try {
                        if (codeService.generateByStream()) {
                            // 在 handleAskButtonClick 中调用流式生成的地方
                            codeService.generateCommitMessageStream(prompt,
                                    token -> {
                                        messageBuilder.append(token);
                                        String fullMarkdown = messageBuilder.toString();
                                        updateResult(fullMarkdown); // 每次都传完整文本，避免断句
                                    },
                                    this::handleErrorResponse);

                        }
                    } catch (Exception e) {
                        handleErrorResponse(e);
                    }
                }

                private void handleErrorResponse(Throwable error) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        IdeaDialogUtil.showError(project, "处理失败: " + error.getMessage(), "Error");
                    });
                }
            });
        } catch (Exception e) {
            Messages.showMessageDialog(project, "处理失败: " + e.getMessage(), "Error", Messages.getErrorIcon());
        }
    }
}