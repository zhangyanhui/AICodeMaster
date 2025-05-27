package com.yohannzhang.aigit.ui;

import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.messages.MessageBusConnection;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.yohannzhang.aigit.config.ApiKeyConfigurableUI;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.config.ChatHistoryService;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import com.yohannzhang.aigit.util.OpenAIUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.icons.AllIcons;
import javax.swing.DefaultListModel;
import javax.swing.ListSelectionModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

public class CombinedWindowFactory implements ToolWindowFactory, EditorColorsListener {
    private MessageBusConnection messageBusConnection; // 用于订阅事件总线
    private static final CodeUtil CODE_UTIL = new CodeUtil();
    private static final Font BUTTON_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Color BUTTON_COLOR = new Color(32, 86, 105);
    private static final Color BUTTON_HOVER_COLOR = new Color(143, 0, 200);

    private JTextArea questionTextArea;
    private JPanel outputPanel;
    private final StringBuilder messageBuilder = new StringBuilder();
    private JEditorPane markdownViewer; // 使用 JEditorPane 替换 JBCefBrowser
    private Color ideBackgroundColor; // 缓存 IDE 背景色

    // 初始化 Flexmark 配置（仅执行一次）
    private static final Parser parser;
    private static final HtmlRenderer renderer;
    //    private JLabel loadingLabel; // 新增成员变量
//    private boolean isInitialResponse = true; // 标记是否是第一次响应（即刚提交问题）
    private JButton askButton;
    private JButton cancelButton; // 新增取消按钮成员变量
    private JComboBox<String> modelComboBox;
    private float fontSize;
    private Color ideFontColor;

    // Add new field for chat history
    private final StringBuilder chatHistory = new StringBuilder();
    private String currentAnswer = ""; // 添加新字段来跟踪当前答案

    // Add field for history panel
    private JPanel historyPanel;
    private JList<HistoryItem> historyList;
    private DefaultListModel<HistoryItem> historyListModel;
    private boolean isHistoryView = false;

    private static final Map<Project, CombinedWindowFactory> instances = new HashMap<>();

    private Color codeBlockBackground; // 添加新的成员变量

    static {
        MutableDataSet options = new MutableDataSet();
        // 配置代码块渲染
        options.set(HtmlRenderer.FENCED_CODE_LANGUAGE_CLASS_PREFIX, "language-");
        options.set(HtmlRenderer.FENCED_CODE_NO_LANGUAGE_CLASS, "nohighlight");
        options.set(HtmlRenderer.CODE_STYLE_HTML_OPEN, "<pre><code class=\"language-java\">");
        options.set(HtmlRenderer.CODE_STYLE_HTML_CLOSE, "</code></pre>");
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
        // 保存实例
        instances.put(project, this);
        
        // 初始化 IDE 背景色
        initIdeBackgroundColor();
        
        // 创建消息连接并监听主题变化
        messageBusConnection = project.getMessageBus().connect();
        messageBusConnection.subscribe(EditorColorsManager.TOPIC, this);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ideBackgroundColor);

        // 创建输出面板
        JPanel outputPanel = createOutputPanel(project);
        mainPanel.add(outputPanel, BorderLayout.CENTER);

        // 创建输入面板
        JPanel inputPanel = createInputPanel(project);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        // 创建内容
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(mainPanel, "", false);
        
        // 添加内容到工具窗口
        toolWindow.getContentManager().addContent(content);

        // 设置窗口工厂
        AIGuiComponent.getInstance(project).setWindowFactory(this);

        // 确保在 EDT 线程中刷新 UI
        ApplicationManager.getApplication().invokeLater(() -> {
            mainPanel.revalidate();
            mainPanel.repaint();
        });
    }
    public void showWelcomePage() {
        String welcomeHtml = readResourceFile("welcome.html");
        markdownViewer.setText(welcomeHtml);
        isHistoryView = false; // 确保不在历史视图

        // 刷新UI确保显示正确
        outputPanel.removeAll();
        outputPanel.add(markdownViewer, BorderLayout.CENTER);
        outputPanel.revalidate();
        outputPanel.repaint();
    }

    private void onThemeChanged(EditorColorsScheme scheme) {
        ApplicationManager.getApplication().invokeLater(() -> {
            initIdeBackgroundColor(); // 更新主题色
            refreshUIOnThemeChange(); // 刷新 UI 样式
        });
    }

    @Override
    public void globalSchemeChange(EditorColorsScheme scheme) {
        onThemeChanged(scheme);
    }

    private void refreshUIOnThemeChange() {
        if (questionTextArea != null) {
            // 只有在不是显示占位符文本时才更新前景色
            if (!questionTextArea.getText().equals("输入问题，点击提交按钮发送")) {
                questionTextArea.setForeground(ideFontColor);
            } else {
                questionTextArea.setForeground(new Color(180, 180, 180));
            }
            questionTextArea.setBackground(ideBackgroundColor);
        }
        if (outputPanel != null) {
            outputPanel.setBackground(ideBackgroundColor);
            outputPanel.setForeground(ideFontColor);
            // 更新输出面板边框颜色
            outputPanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                    "AI 助手"
            ));
        }
        if (askButton != null && cancelButton != null) {
            askButton.setBackground(BUTTON_COLOR);
            askButton.setForeground(ideFontColor);
            cancelButton.setBackground(BUTTON_COLOR);
            cancelButton.setForeground(ideFontColor);
        }
        if (modelComboBox != null) {
            modelComboBox.setBackground(ideBackgroundColor);
            modelComboBox.setForeground(ideFontColor);
        }

        // 刷新所有面板的背景色
        Component[] components = questionTextArea.getParent().getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                comp.setBackground(ideBackgroundColor);
                // 递归刷新子组件
                refreshComponentBackground(comp);
            }
        }

        // 更新 Markdown 查看器的主题
        String script = "document.documentElement.style.setProperty('--font-size', '" + fontSize + "px');" +
                "document.documentElement.style.setProperty('--workspace-color', '" + toHex(ideBackgroundColor) + "');" +
                "document.documentElement.style.setProperty('--idefont-color', '" + toHex(ideFontColor) + "');" +
                "document.body.style.backgroundColor = '" + toHex(ideBackgroundColor) + "';" +
                "document.getElementById('content').style.color = '" + toHex(ideFontColor) + "';" +
                "document.querySelectorAll('.feature').forEach(el => el.style.backgroundColor = '" + toHex(ideBackgroundColor) + "');" +
                "document.querySelectorAll('.feature-title').forEach(el => el.style.color = '" + toHex(ideFontColor) + "');" +
                "document.querySelectorAll('.feature-desc').forEach(el => el.style.color = '" + toHex(ideFontColor) + "');" +
                "document.querySelectorAll('.welcome-title').forEach(el => el.style.color = '" + toHex(ideFontColor) + "');" +
                "document.querySelectorAll('.welcome-subtitle').forEach(el => el.style.color = '" + toHex(ideFontColor) + "');";

        if (markdownViewer != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                markdownViewer.setText(script);
            });
        }
    }

    private void refreshComponentBackground(Component component) {
        if (component == null) return;
        
        component.setBackground(ideBackgroundColor);
        if (component instanceof JComboBox) {
            component.setForeground(ideFontColor);
        }
        
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                refreshComponentBackground(child);
            }
        }
    }

    private void initIdeBackgroundColor() {
        EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
        ideBackgroundColor = colorsScheme.getDefaultBackground();
        fontSize = colorsScheme.getEditorFontSize();

        // 计算亮度
        int rgb = ideBackgroundColor.getRGB();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

        // 动态调整前景色和背景色
        if (brightness < 0.5) {
            ideFontColor = Color.WHITE;
            ideBackgroundColor = new Color(43, 43, 43);
            codeBlockBackground = new Color(30, 30, 30); // 暗色主题下的代码块背景
        } else {
            ideFontColor = Color.BLACK;
            ideBackgroundColor = new Color(250, 250, 250);
            codeBlockBackground = new Color(245, 245, 245); // 亮色主题下的代码块背景
        }
    }

    public void updateResult(String markdownResult) {
        if (markdownViewer == null) return;

        // 解析 Markdown 到 HTML
        com.vladsch.flexmark.util.ast.Document document = parser.parse(markdownResult);
        String htmlBody = renderer.render(document);

        // 只用 CSS 美化代码块，不用 JS
        String htmlWithHighlight = "<!DOCTYPE html><html><head>" +
                "<meta charset='UTF-8'>" +
                "<style>" +
                readResourceFile("prism.css") +
                "body { background-color: " + toHex(ideBackgroundColor) + "; color: " + toHex(ideFontColor) + "; font-size: 14px; }" +
                "pre { background-color: " + toHex(codeBlockBackground) + "; padding: 10px; border-radius: 5px; margin: 10px 0; border: 1px solid " + toHex(new Color(200, 200, 200)) + "; position: relative; }" +
                "code { font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace; font-size: 12px; }" +
                ".copy-button { position: absolute; top: 5px; right: 5px; padding: 5px 10px; background: #e1e4e8; border: none; border-radius: 3px; cursor: pointer; }" +
                "</style>" +
                "</head><body>" +
                htmlBody +
                "<script>" +
                "function addCopyButtons() {" +
                "  document.querySelectorAll('pre code').forEach((block) => {" +
                "    const button = document.createElement('button');" +
                "    button.className = 'copy-button';" +
                "    button.textContent = '复制';" +
                "    block.parentNode.style.position = 'relative';" +
                "    block.parentNode.appendChild(button);" +
                "    button.addEventListener('click', () => {" +
                "      navigator.clipboard.writeText(block.textContent);" +
                "      button.textContent = '已复制';" +
                "      setTimeout(() => button.textContent = '复制', 2000);" +
                "    });" +
                "  });" +
                "}" +
                "addCopyButtons();" +
                "</script>" +
                "</body></html>";

        // 预加载 HTML 内容
        final String finalHtml = htmlWithHighlight;
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                markdownViewer.setContentType("text/html");
                markdownViewer.setText(finalHtml);
                markdownViewer.setCaretPosition(0);
                // 确保内容完全加载
                markdownViewer.revalidate();
                markdownViewer.repaint();
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        outputPanel = new JPanel(new BorderLayout(10, 10));
        outputPanel.setBackground(ideBackgroundColor);
        outputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                "AI 助手"
        ));

        markdownViewer = new JEditorPane();
        markdownViewer.setContentType("text/html");
        markdownViewer.setEditable(false);
        markdownViewer.setBackground(ideBackgroundColor);
        markdownViewer.setForeground(ideFontColor);
        markdownViewer.setFont(new Font("SansSerif", Font.PLAIN, (int)fontSize));
        
        // 设置 HTML 编辑器属性
        markdownViewer.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        markdownViewer.putClientProperty("JEditorPane.w3cLengthUnits", Boolean.TRUE);
        
        String initialHtml = "<html><body style='background-color: " + toHex(ideBackgroundColor) + 
                           "; color: " + toHex(ideFontColor) + ";'>" +
                           "<div style='padding: 20px;'>" +
                           "<h2>欢迎使用 AI Code Master</h2>" +
                           "<p>请在下方输入框中输入您的问题。</p>" +
                           "</div></body></html>";
        
        markdownViewer.setText(initialHtml);

        JBScrollPane scrollPane = new JBScrollPane(markdownViewer);
        scrollPane.setBorder(null);
        outputPanel.add(scrollPane, BorderLayout.CENTER);

        return outputPanel;
    }

    private void toggleHistoryView() {
        if (!isHistoryView) {
            // 显示历史面板
            if (historyPanel == null) {
                createHistoryPanel();
            }
            outputPanel.remove(markdownViewer);
            outputPanel.add(historyPanel, BorderLayout.CENTER);
            updateHistoryList();
        } else {
            // 显示主内容
            outputPanel.remove(historyPanel);
            outputPanel.add(markdownViewer, BorderLayout.CENTER);
            if (chatHistory.length() == 0) {
//                showWelcomeContent();
            }
        }
        isHistoryView = !isHistoryView;
        outputPanel.revalidate();
        outputPanel.repaint();
    }


    private void showWelcomeContent() {
        // Reload the initial welcome HTML content
        String EMPTY_HTML = readResourceFile("welcome.html"); // 假设你把欢迎页面内容放在了资源文件中
        ApplicationManager.getApplication().invokeLater(() -> {
            markdownViewer.setText(EMPTY_HTML);
        });
    }

    private void createHistoryPanel() {
        historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBackground(ideBackgroundColor);
        historyPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        historyListModel = new DefaultListModel<>();
        historyList = new JList<>(historyListModel);
        historyList.setBackground(ideBackgroundColor);
        historyList.setForeground(ideFontColor);
        historyList.setFont(new Font("SansSerif", Font.PLAIN, 14));
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setFixedCellHeight(60);
        historyList.setCellRenderer(new HistoryListCellRenderer());

        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = historyList.getSelectedIndex();
                if (selectedIndex != -1) {
                    String[] historyItems = chatHistory.toString().split(
                            "<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>"
                    );

                    if (selectedIndex < historyItems.length) {
                        String questionItem = historyItems[selectedIndex];
                        String answerItem = (selectedIndex + 1 < historyItems.length) ?
                                historyItems[selectedIndex + 1] : "";

                        String fullContent = questionItem +
                                "<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>" +
                                answerItem;

                        updateResult(fullContent);

                        if (isHistoryView) {
                            toggleHistoryView();
                        }
                    }
                }
            }
        });

        JScrollPane listScrollPane = new JBScrollPane(historyList);
        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        historyPanel.add(listScrollPane, BorderLayout.CENTER);
    }

    private void updateHistoryList() {
        historyListModel.clear();
        String[] historyItems = chatHistory.toString().split("<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>");
        
        for (String item : historyItems) {
            if (!item.trim().isEmpty()) {
                // 提取问题和时间戳
                int qStart = item.indexOf("<strong style='color: #4CAF50;'>Q:</strong>");
                if (qStart != -1) {
                    int qEnd = item.indexOf("</div>", qStart);
                    if (qEnd != -1) {
                        String question = item.substring(qStart + 40, qEnd).trim();
                        String timestamp = "";
                        int timeStart = item.indexOf("<span style='color: #666; font-size: 0.9em;'>");
                        if (timeStart != -1) {
                            int timeEnd = item.indexOf("</span>", timeStart);
                            if (timeEnd != -1) {
                                timestamp = item.substring(timeStart + 45, timeEnd);
                            }
                        }
                        historyListModel.addElement(new HistoryItem(question, timestamp));
                    }
                }
            }
        }
    }

    private static class HistoryItem {
        private final String question;
        private final String timestamp;

        public HistoryItem(String question, String timestamp) {
            this.question = question;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return question;
        }
    }

    private static class HistoryListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                    int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof HistoryItem) {
                HistoryItem item = (HistoryItem) value;
                JPanel panel = new JPanel(new BorderLayout(5, 5));
                panel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
                panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

                // 创建问题标签
                JLabel questionLabel = new JLabel(item.question);
                questionLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
                questionLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

                // 创建时间戳标签
                JLabel timeLabel = new JLabel(item.timestamp);
                timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
                timeLabel.setForeground(isSelected ? list.getSelectionForeground() : new Color(128, 128, 128));

                // 添加左侧的Q标记
                JLabel qLabel = new JLabel("Q:");
                qLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
                qLabel.setForeground(new Color(76, 175, 80)); // 绿色
                qLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

                // 创建左侧面板
                JPanel leftPanel = new JPanel(new BorderLayout(5, 0));
                leftPanel.setBackground(panel.getBackground());
                leftPanel.add(qLabel, BorderLayout.WEST);
                leftPanel.add(questionLabel, BorderLayout.CENTER);

                panel.add(leftPanel, BorderLayout.CENTER);
                panel.add(timeLabel, BorderLayout.EAST);

                return panel;
            }

            return this;
        }
    }

    private JPanel createInputPanel(Project project) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(ideBackgroundColor);

        // 创建顶部面板
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setBackground(ideBackgroundColor);

        // 创建模型选择下拉框
        String[] clientArr = ApiKeySettings.getInstance().getAvailableModels();
        modelComboBox = new JComboBox<>(clientArr);
        modelComboBox.setSelectedItem(ApiKeySettings.getInstance().getSelectedModule());
        modelComboBox.setPreferredSize(new Dimension(120, 32));
        modelComboBox.setBackground(ideBackgroundColor);
        modelComboBox.setForeground(ideFontColor);
        modelComboBox.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // 创建按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBackground(ideBackgroundColor);
        
        // 创建按钮
        askButton = createStyledButton("提交");
        cancelButton = createStyledButton("取消");
        cancelButton.setVisible(false);

        // 确保按钮文字显示
        askButton.setText("提交");
        cancelButton.setText("取消");

        // 添加按钮到面板
        buttonPanel.add(askButton);
        buttonPanel.add(cancelButton);

        // 添加组件到顶部面板
        topPanel.add(modelComboBox, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        // 创建输入区域
        questionTextArea = new JTextArea(3, 50);
        questionTextArea.setLineWrap(true);
        questionTextArea.setWrapStyleWord(true);
        questionTextArea.setBackground(ideBackgroundColor);
        questionTextArea.setForeground(new Color(180, 180, 180));
        questionTextArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        
        String placeholderText = "输入问题，点击提交按钮发送";
        questionTextArea.setText(placeholderText);

        // 添加焦点监听器
        questionTextArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (questionTextArea.getText().equals(placeholderText)) {
                    questionTextArea.setText("");
                    questionTextArea.setForeground(ideFontColor);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (questionTextArea.getText().isEmpty()) {
                    questionTextArea.setText(placeholderText);
                    questionTextArea.setForeground(new Color(180, 180, 180));
                }
            }
        });

        // 创建滚动面板
        JBScrollPane scrollPane = new JBScrollPane(questionTextArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        // 组装面板
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 添加事件监听器
        askButton.addActionListener(e -> handleAskButtonClick(project));
        cancelButton.addActionListener(e -> handleCancel());

        return panel;
    }


    private void handleCancel() {
        OpenAIUtil.cancelRequest();
        ApplicationManager.getApplication().invokeLater(() -> {
//            loadingLabel.setVisible(false);
            askButton.setVisible(true);
            cancelButton.setVisible(false);
        });
    }


    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        // 不设置 setFont、setForeground、setBackground、setOpaque、setContentAreaFilled
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(80, 32));
        button.setText(text);

        // 添加鼠标事件监听器
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
        if (question.isEmpty() || question.equals("输入问题，点击提交按钮发送")) {
            String errorMessage = "<div style='color: #ff6b6b; padding: 10px; background-color: rgba(255, 107, 107, 0.1); border-radius: 4px;'>请输入问题内容</div>";
            updateResult(errorMessage);
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            askButton.setVisible(false);
            cancelButton.setVisible(true);
        });

        // 清空历史记录，开始新的对话
        chatHistory.setLength(0);
        messageBuilder.setLength(0);
        currentAnswer = "";

        // 格式化问题和答案的显示
        String formattedQuestion = String.format(
            "<div class='chat-item question-item' style='margin: 10px 0; padding: 10px; border-left: 3px solid #4CAF50; position: relative;'>" +
            "<div style='display: flex; align-items: center; margin-bottom: 5px;'>" +
            "<strong style='color: #4CAF50;'>Q:</strong>" +
            "</div>" +
            "<div style='margin-left: 20px;'>%s</div>" +
            "</div>",
            question
        );

        chatHistory.append(formattedQuestion);
        chatHistory.append("<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>");

        CodeService codeService = new CodeService();
        String formattedCode = CODE_UTIL.formatCode(question);
        String prompt = String.format("根据提出的问题作出回答，用中文回答；若需编程，请给出示例，以C++作为默认编程语言输出；问题如下：%s", formattedCode);

        try {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "处理中", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    messageBuilder.setLength(0); // 重置内容构建器
                    currentAnswer = ""; // 重置当前答案
                    try {
                        if (codeService.generateByStream()) {
                            codeService.generateCommitMessageStream(
                                prompt,
                                token -> {
                                    messageBuilder.append(token);
                                    currentAnswer = messageBuilder.toString();
                                    // 格式化完整的答案
                                    String formattedAnswer = String.format(
                                        "<div class='chat-item answer-item' style='margin: 10px 0; padding: 10px; border-left: 3px solid #2196F3;'>" +
                                        "<div style='display: flex; align-items: center; margin-bottom: 5px;'>" +
                                        "<strong style='color: #2196F3;'>A:</strong>" +
                                        "</div>" +
                                        "<div style='margin-left: 20px;'>%s</div>" +
                                        "</div>",
                                        currentAnswer
                                    );
                                    
                                    // 更新显示
                                    String displayContent = formattedQuestion + 
                                        "<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>" +
                                        formattedAnswer;
                                    updateResult(displayContent);
                                },
                                this::handleErrorResponse,
                                () -> {
                                    // 完成时保存历史记录
                                    saveHistory(question, currentAnswer);
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        resetButton();
                                    });
                                }
                            );
                        }
                    } catch (Exception e) {
                        handleErrorResponse(e);
                    }
                }

                private void handleErrorResponse(Throwable error) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (project != null && !project.isDisposed()) {
                            Messages.showErrorDialog(project, "处理失败: " + error.getMessage(), "Error");
                        } else {
                            Messages.showErrorDialog("处理失败: " + error.getMessage(), "Error");
                        }
                    });
                }
            });
        } catch (Exception e) {
            Messages.showMessageDialog(project, "处理失败: " + e.getMessage(), "Error", Messages.getErrorIcon());
        }
    }

    //把以下两行抽成一个方法
    public void resetButton() {
        askButton.setVisible(true);
        cancelButton.setVisible(false);
    }

    public void submitButton() {
        askButton.setVisible(false);
        cancelButton.setVisible(true);
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    // 在CombinedWindowFactory中添加:
    private void loadHistory() {
        ChatHistoryService service = ChatHistoryService.getInstance();
        chatHistory.setLength(0); // 清空现有历史

        // 按时间倒序加载历史记录（最新记录在前）
        service.getState().chatHistoryMap.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
                .forEach(entry -> {
                    String q = entry.getKey();
                    String a = entry.getValue();

                    // 格式化历史记录（保持与实时交互相同的样式）
                    appendHistoryItem(q, a);
                });

        updateHistoryList();
    }

    private void appendHistoryItem(String question, String answer) {
        chatHistory.append("<div class='history-item'>")
                .append("<div class='question'>").append(question).append("</div>")
                .append("<div class='answer'>").append(answer).append("</div>")
                .append("</div>");
    }




    private void saveHistory(String question, String answer) {
        ChatHistoryService service = ChatHistoryService.getInstance();
        // 只保存问答对，不保存原始Map字符串
        service.addChatRecord(question, answer);
    }

    public static CombinedWindowFactory getInstance(Project project) {
        return instances.get(project);
    }
}