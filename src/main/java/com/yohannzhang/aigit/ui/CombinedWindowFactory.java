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
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.Arrays;

public class CombinedWindowFactory implements ToolWindowFactory {
    private static final CodeUtil CODE_UTIL = new CodeUtil();

    private static final Font BUTTON_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Color BUTTON_COLOR = new Color(0, 120, 215);
    private static final Color BUTTON_HOVER_COLOR = new Color(0, 100, 200);

    private JTextArea questionTextArea;
    private final StringBuilder messageBuilder = new StringBuilder();
    private transient JEditorPane htmlViewer; // 改为实例变量

    private static EditorColorsScheme getCurrentColorScheme() {
        return EditorColorsManager.getInstance().getGlobalScheme();
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultConstraints();

        panel.add(createOutputPanel(project), gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.3;
        panel.add(createInputPanel(project), gbc);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
        AIGuiComponent.getInstance(project).setWindowFactory(this);
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
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setDoubleBuffered(true);
        outputPanel.setBorder(BorderFactory.createTitledBorder("输出结果"));

        EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
        Color backgroundColor = colorsScheme.getDefaultBackground();
        outputPanel.setBackground(backgroundColor);

        htmlViewer = new JEditorPane();
        htmlViewer.setContentType("text/html");
        htmlViewer.setEditable(false);
        htmlViewer.setBackground(backgroundColor);
        htmlViewer.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE); // 启用 JS

        JScrollPane scrollPane = new JBScrollPane(htmlViewer);
        outputPanel.add(scrollPane, BorderLayout.CENTER);

        return outputPanel;
    }


    private JPanel createInputPanel(Project project) {
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("输入问题"));

        EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
        Color backgroundColor = colorsScheme.getDefaultBackground();
        inputPanel.setBackground(backgroundColor);

        questionTextArea = new JTextArea(5, 50);
        questionTextArea.setEditable(true);
        questionTextArea.setLineWrap(true);
        questionTextArea.setWrapStyleWord(true);
        questionTextArea.setBackground(backgroundColor);

        inputPanel.add(new JBScrollPane(questionTextArea), BorderLayout.CENTER);
        inputPanel.add(createButtonPanel(project), BorderLayout.SOUTH);

        return inputPanel;
    }

    private JPanel createButtonPanel(Project project) {
        JButton askButton = createStyledButton("提交问题");
        askButton.addActionListener(event -> handleAskButtonClick(project));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(askButton);
        return buttonPanel;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(BUTTON_FONT);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(BUTTON_COLOR);
        button.setForeground(Color.WHITE);

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(BUTTON_HOVER_COLOR);
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
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
                    if (codeService.generateByStream()) {
                        messageBuilder.setLength(0);
                        try {
                            codeService.generateCommitMessageStream(
                                    prompt,
                                    this::handleTokenResponse,
                                    this::handleErrorResponse
                            );
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                private void handleTokenResponse(String token) {
                    messageBuilder.append(token);
                    updateResult(messageBuilder.toString());
                }

                private void handleErrorResponse(Throwable error) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            showError(project, "Error generating commit message: " + error.getMessage())
                    );
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Messages.showMessageDialog("处理失败！", "错误", Messages.getErrorIcon());
        }
    }

    private void showError(Project project, String message) {
        IdeaDialogUtil.showError(project, message, "Error");
    }

    public static String renderMarkdownToHtmlWithHighlighting(String markdown) {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                FootnoteExtension.create()
        ));

        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        return renderer.render(parser.parse(markdown));
    }

    public synchronized void updateResult(String markdownResult) {
//        buffer.append(markdownResult);
        ApplicationManager.getApplication().invokeLater(() -> {
            if (htmlViewer == null) return;


            // 使用 flexmark-java 渲染 Markdown 为 HTML
            String html = renderMarkdownToHtmlWithHighlighting(markdownResult);

            URL iconUrl = CombinedWindowFactory.class.getResource("/icons/fuzhi.png");

            // 获取默认文本前景色
            Color foreground = getCurrentColorScheme().getDefaultForeground();
// 获取默认背景色
            Color background = getCurrentColorScheme().getDefaultBackground();

// 手动设置常见语法元素颜色（可根据实际主题调整）
            Color keywordColor = new Color(0, 0, 255); // 蓝色
            Color stringColor = new Color(163, 21, 21); // 红色
            Color commentColor = new Color(0, 128, 0); // 绿色
            Color numberColor = new Color(128, 0, 128); // 紫色
            Color tagColor = new Color(128, 0, 0); // 深红色
            Color attrColor = new Color(128, 0, 128); // 紫色
            Color builtInColor = new Color(43, 145, 175); // 蓝绿色

            String htmlWithStyles = """
                    <!DOCTYPE html>
                    <html lang="zh-CN">
                    <head>
                        <meta charset="UTF-8">
                        <style>
                            body {
                                color: %s;
                                background-color: %s;
                                font-family: sans-serif;
                            }
                            pre {
                                border: 1px solid #ccc;
                                padding: 10px;
                                background-color: %s;
                                overflow-x: auto;
                                position: relative; /* 确保按钮定位基于此 */
                            }
                            code {
                                font-family: monospace;
                                color: %s;
                            }
                            .hl-keyword { color: %s; }
                            .hl-string   { color: %s; }
                            .hl-comment  { color: %s; }
                            .hl-number   { color: %s; }
                            .hl-tag      { color: %s; }
                            .hl-attr     { color: %s; }
                            .hl-built_in { color: %s; }
                    
                            .copy-button {
                                position: absolute;
                                top: 5px;
                                right: 5px;
                                background-color: transparent;
                                border: none;
                                padding: 0;
                                cursor: pointer;
                            }
    
                            .copy-button img {
                                width: 20px;   /* 修改尺寸 */
                                height: 20px;
                            }
                    
                        </style>
                        <script>
                            function copyCode(element) {
                                const codeElement = element.parentElement.querySelector('code');
                                const range = document.createRange();
                                range.selectNodeContents(codeElement);
                                const selection = window.getSelection();
                                selection.removeAllRanges();
                                selection.addRange(range);
                    
                                try {
                                    document.execCommand('copy');
                                } finally {
                                    selection.removeAllRanges();
                                }
                            }
                        </script>
                    
                    </head>
                    <body>
                        %s
                    </body>
                    </html>
                    """.formatted(
                    toHexString(foreground),
                    toHexString(background),
                    toHexString(background.brighter()),
                    toHexString(foreground),
                    toHexString(keywordColor),
                    toHexString(stringColor),
                    toHexString(commentColor),
                    toHexString(numberColor),
                    toHexString(tagColor),
                    toHexString(attrColor),
                    toHexString(builtInColor),
                    html.replaceAll("<pre>", "<pre><button class=\"copy-button\" onclick=\"copyCode(this)\"><img src=" + iconUrl + " width=\"20\" height=\"20\"/></button>")
            );


            System.out.println("===" + htmlWithStyles);
            // 滚动到底部
            SwingUtilities.invokeLater(() -> {
                if (htmlViewer != null) {
                    htmlViewer.setText(htmlWithStyles);
                    // 只刷新而不重新布局
                    htmlViewer.revalidate();
                    htmlViewer.repaint();
                }
                JScrollBar scrollBar = ((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, htmlViewer)).getVerticalScrollBar();
                if (scrollBar != null) {
                    scrollBar.setValue(scrollBar.getMaximum());
                }
            });

//            buffer.setLength(0); // 清空缓冲区
        });
    }


    private static String toHexString(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

}
