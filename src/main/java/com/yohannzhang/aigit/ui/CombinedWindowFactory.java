package com.yohannzhang.aigit.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.messages.MessageBusConnection;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.config.ChatHistoryService;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.service.impl.OllamaService;
import com.yohannzhang.aigit.service.RagService;
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import com.yohannzhang.aigit.util.OpenAIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class CombinedWindowFactory implements ToolWindowFactory, EditorColorsListener {
    private static final Map<Project, CombinedWindowFactory> instances = Collections.synchronizedMap(new HashMap<>());
    private MessageBusConnection messageBusConnection;
    private static final CodeUtil CODE_UTIL = new CodeUtil();
    private static final Font BUTTON_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Color BUTTON_COLOR = new Color(32, 86, 105);
    private static final Color BUTTON_HOVER_COLOR = new Color(143, 0, 200);

    //    private JTextArea questionTextArea;
//    private JPanel outputPanel;
//    private JBCefBrowser markdownViewer;
    private Color ideBackgroundColor;
    private Project currentProject;
    private boolean isDisposed = false;

    // 初始化 Flexmark 配置（仅执行一次）
    private static final Parser parser;
    private static final HtmlRenderer renderer;
    //    private JLabel loadingLabel; // 新增成员变量
//    private boolean isInitialResponse = true; // 标记是否是第一次响应（即刚提交问题）
//    private JButton askButton;
//    private JButton cancelButton; // 新增取消按钮成员变量
//    private JComboBox<String> modelComboBox;
//    private JComboBox<String> modelSelectComboBox; // 新增成员变量
    private float fontSize;
    private Color ideFontColor;
    public static final Map<Project, UIState> uiStates = Collections.synchronizedMap(new HashMap<>());

    public static class UIState {
        JTextArea questionTextArea;
        JButton askButton;
        JButton cancelButton;
        public JComboBox<String> modelComboBox;
        public JComboBox<String> modelSelectComboBox;
        JPanel outputPanel;
        JBCefBrowser markdownViewer;
        StringBuilder chatHistory = new StringBuilder();
        StringBuilder currentAnswer = new StringBuilder();
        boolean isHistoryView = false;
        JPanel historyPanel;
        DefaultListModel<HistoryItem> historyListModel;
        private final StringBuilder messageBuilder = new StringBuilder();
        Timer loadingTimer;
        JPanel loadingPanel;
    }

    static {
        MutableDataSet options = new MutableDataSet();
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        if (isDisposed) return;

        this.currentProject = project;

        // 清理已有实例
        CombinedWindowFactory existingInstance = instances.get(project);
        if (existingInstance != null && existingInstance != this) {
            existingInstance.dispose();
        }

        // 初始化 IDE 背景色
        initIdeBackgroundColor();

        // 创建消息连接并监听主题变化
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        messageBusConnection = project.getMessageBus().connect();
        messageBusConnection.subscribe(EditorColorsManager.TOPIC, this);

        // 注册项目监听器
        project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosed(@NotNull Project project) {
                // 当项目关闭时，从映射中移除实例
                instances.remove(project);
            }
        });

        // 添加RAG按钮到titleActions
        AnAction ragAction = new AnAction("RAG Analysis", "Perform RAG analysis on project", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                RagService ragService = new RagService(project);
                ragService.performRag();
                // Show success message
                Messages.showInfoMessage(project, 
                    "RAG analysis completed. Results saved in 'rag_results' directory.", 
                    "RAG Analysis");
            }
        };

        toolWindow.setTitleActions(Collections.singletonList(ragAction));

        JPanel panel = new JPanel(new GridBagLayout());
        // 使用 GridBagLayout 布局管理器，适应复杂 UI 结构
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.9; // 输出面板占大部分空间
        gbc.fill = GridBagConstraints.BOTH;

        panel.add(createOutputPanel(project), gbc);
        gbc.gridy = 1;
        gbc.weighty = 0.1;
        panel.add(createInputPanel(project), gbc);

        Content content = toolWindow.getContentManager().getFactory().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        instances.put(project, this);
    }

    @Override
    public void globalSchemeChange(EditorColorsScheme scheme) {
        onThemeChanged(scheme);
    }

    private void onThemeChanged(EditorColorsScheme scheme) {
        ApplicationManager.getApplication().invokeLater(() -> {
            initIdeBackgroundColor(); // 更新主题色
            refreshUIOnThemeChange(currentProject); // 刷新 UI 样式
        });
    }

    private void refreshUIOnThemeChange(Project project) {
        UIState state = uiStates.get(project);
        if (state == null) return;

        // 更新输出面板
        if (state.outputPanel != null) {
            state.outputPanel.setBackground(ideBackgroundColor);
            refreshComponentBackground(state.outputPanel);
        }

        // 更新问题输入区域
        if (state.questionTextArea != null) {
            state.questionTextArea.setBackground(ideBackgroundColor);
            state.questionTextArea.setForeground(ideFontColor);
        }

        // 更新历史面板
        if (state.historyPanel != null) {
            state.historyPanel.setBackground(ideBackgroundColor);
            refreshComponentBackground(state.historyPanel);
        }

        // 更新Markdown查看器
        if (state.markdownViewer != null) {
            // 构建CSS变量更新脚本
            String script = String.format(
                    "document.documentElement.style.setProperty('--workspace-color', '%s');" +
                            "document.documentElement.style.setProperty('--idefont-color', '%s');" +
                            "document.documentElement.style.setProperty('--font-size', '%spx');" +
                            "document.body.style.backgroundColor = '%s';" +
                            "document.body.style.color = '%s';" +
                            "document.querySelectorAll('pre').forEach(pre => {" +
                            "    pre.style.backgroundColor = '%s';" +
                            "    pre.style.color = '%s';" +
                            "});" +
                            "document.querySelectorAll('code').forEach(code => {" +
                            "    code.style.backgroundColor = '%s';" +
                            "    code.style.color = '%s';" +
                            "});",
                    toHex(ideBackgroundColor),
                    toHex(ideFontColor),
                    fontSize,
                    toHex(ideBackgroundColor),
                    toHex(ideFontColor),
                    toHex(ideBackgroundColor),
                    toHex(ideFontColor),
                    toHex(ideBackgroundColor),
                    toHex(ideFontColor)
            );

            ApplicationManager.getApplication().invokeLater(() -> {
                String url = state.markdownViewer.getCefBrowser().getURL();

                state.markdownViewer.getCefBrowser().executeJavaScript(script, url, 0);
//                String currentHtml = state.markdownViewer.getCefBrowser().getURL();
//
//                // 重新加载当前内容以应用新主题
//                if (currentHtml != null && !currentHtml.isEmpty()) {
//                    state.markdownViewer.loadHTML(currentHtml);
//                }
            });
        }

        // 更新下拉框
        if (state.modelComboBox != null) {
            state.modelComboBox.setBackground(ideBackgroundColor);
            state.modelComboBox.setForeground(ideFontColor);
        }
        if (state.modelSelectComboBox != null) {
            state.modelSelectComboBox.setBackground(ideBackgroundColor);
            state.modelSelectComboBox.setForeground(ideFontColor);
        }
    }

    private void refreshComponentBackground(Component component) {
        component.setBackground(ideBackgroundColor);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                refreshComponentBackground(child);
            }
        }
    }

    private void initIdeBackgroundColor() {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        ideBackgroundColor = scheme.getDefaultBackground();
        ideFontColor = scheme.getDefaultForeground();
        fontSize = scheme.getEditorFontSize();
    }

    public void updateResult(String markdownResult, Project project) {
        UIState state = getOrCreateState(project);
        if (state.markdownViewer == null) return;

        // Add spacing between code blocks and HTML elements
        String processedMarkdown = markdownResult
                .replace("```\n</div>", "```\n\n</div>")
                .replace("```</div>", "```\n\n</div>")
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
            state.markdownViewer.getCefBrowser().executeJavaScript(script, "about:blank", 0);
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
        UIState state = getOrCreateState(project);
        JPanel outputPanel = new JPanel(new BorderLayout(10, 10));
        outputPanel.setBackground(ideBackgroundColor);

        state.markdownViewer = JBCefBrowser.createBuilder()
                .setUrl("about:blank")
                .build();
        state.markdownViewer.getComponent().setBorder(BorderFactory.createEmptyBorder());

        String welcomeHtml = HtmlTemplateReplacer.replaceCssVariables("empty.html", fontSize, ideBackgroundColor, ideFontColor);

       ApplicationManager.getApplication().invokeLater(() -> {
            state.markdownViewer.loadHTML(welcomeHtml);
        });

        outputPanel.add(state.markdownViewer.getComponent(), BorderLayout.CENTER);
        state.outputPanel = outputPanel;

        return outputPanel;
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
        UIState state = getOrCreateState(project);
        JPanel inputPanel = new JPanel(new BorderLayout(1, 1)); // 调整整体组件间距为 5
        inputPanel.setBackground(ideBackgroundColor);

        String[] clientArr = ApiKeySettings.getInstance().getAvailableModels();
        JComboBox<String> modelComboBox = new JComboBox<>(clientArr);
        modelComboBox.setSelectedItem(ApiKeySettings.getInstance().getSelectedClient());
        modelComboBox.setPreferredSize(new Dimension(120, 32));
        modelComboBox.setBackground(ideBackgroundColor);
        modelComboBox.setForeground(ideFontColor);
        modelComboBox.setFont(new Font("SansSerif", Font.PLAIN, 12));

        String[] modelArr = Constants.CLIENT_MODULES.get(modelComboBox.getSelectedItem());
        JComboBox<String> modelSelectComboBox = new JComboBox<>(modelArr);
        modelSelectComboBox.setSelectedItem(ApiKeySettings.getInstance().getSelectedModule());
        modelSelectComboBox.setPreferredSize(new Dimension(120, 32));
        modelSelectComboBox.setBackground(ideBackgroundColor);
        modelSelectComboBox.setForeground(ideFontColor);
        modelSelectComboBox.setFont(new Font("SansSerif", Font.PLAIN, 12));

        modelComboBox.addActionListener(e -> {
            String selectedClient = (String) modelComboBox.getSelectedItem();
            String[] arr = Constants.CLIENT_MODULES.get(modelComboBox.getSelectedItem());
            modelSelectComboBox.removeAllItems();
            for (String module : arr) {
                modelSelectComboBox.addItem(module);
            }
            ApiKeySettings.getInstance().setSelectedClient(selectedClient);
        });

        modelSelectComboBox.addActionListener(e -> {
            String selectedModel = (String) modelSelectComboBox.getSelectedItem();
            ApiKeySettings.getInstance().setSelectedModule(selectedModel);
        });

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.setBackground(ideBackgroundColor);
        leftPanel.add(modelComboBox);
        leftPanel.add(modelSelectComboBox);

        JButton askButton = createStyledButton("提交");
        JButton cancelButton = createStyledButton("取消");
        cancelButton.setBackground(new Color(231, 76, 60));
        cancelButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(231, 76, 60), 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
        cancelButton.setVisible(false);

        askButton.addActionListener(e -> handleAskButtonClick(project));
        cancelButton.addActionListener(e -> handleCancel(project));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBackground(ideBackgroundColor);
        buttonPanel.add(askButton);
        buttonPanel.add(cancelButton);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ideBackgroundColor);
        topPanel.add(leftPanel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        JTextArea questionTextArea = new JTextArea(3, 50);
        questionTextArea.setLineWrap(true);
        questionTextArea.setWrapStyleWord(true);
        questionTextArea.setBackground(ideBackgroundColor);
        questionTextArea.setForeground(new Color(180, 180, 180));
        questionTextArea.requestFocusInWindow();

        String placeholderText = "输入问题，点击提交按钮发送";
        questionTextArea.setText(placeholderText);

        questionTextArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (questionTextArea.getText().equals(placeholderText)) {
                        questionTextArea.setText("");
                        questionTextArea.setForeground(ideFontColor);
                    }
                });
            }

            @Override
            public void focusLost(FocusEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (questionTextArea.getText().isEmpty()) {
                        questionTextArea.setText(placeholderText);
                        questionTextArea.setForeground(new Color(180, 180, 180));
                    }
                });
            }
        });

        questionTextArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        // 添加文档监听器用于触发补全
//        questionTextArea.getDocument().addDocumentListener(new DocumentAdapter() {
//            @Override
//            protected void textChanged(@NotNull DocumentEvent e) {
//                ApplicationManager.getApplication().invokeLater(() -> {
//                    if (SwingUtilities.isEventDispatchThread()) {
//                        showNaturalLanguageSuggestions(project, questionTextArea);
//                    }
//                });
//            }
//        });

        questionTextArea.setEditable(true);

        // 设置边距，控制面板与窗口边缘的距离
//        inputPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        inputPanel.add(topPanel, BorderLayout.SOUTH);
        inputPanel.add(new JBScrollPane(questionTextArea), BorderLayout.CENTER);

        refreshComponentBackground(inputPanel);

        // 保存到状态
        state.modelComboBox = modelComboBox;
        state.modelSelectComboBox = modelSelectComboBox;
        state.askButton = askButton;
        state.cancelButton = cancelButton;
        state.questionTextArea = questionTextArea;

        return inputPanel;
    }


    private UIState getOrCreateState(Project project) {
        return uiStates.computeIfAbsent(project, k -> new UIState());
    }


    private void handleCancel(Project project) {
        OpenAIUtil.cancelRequest();
        OllamaService.cancelRequest();
        UIState state = uiStates.get(project);
        if (state != null && state.askButton != null && state.cancelButton != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
//            loadingLabel.setVisible(false);
                state.askButton.setVisible(true);
                state.cancelButton.setVisible(false);
            });
        }


    }


    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setOpaque(true);
//        button.setForeground(Color.WHITE);
//        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 设置按钮的 preferredSize
        button.setPreferredSize(new Dimension(80, 32));

        // 设置默认背景颜色和边框
//        button.setBackground(new Color(41, 185, 163));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(214, 236, 221), 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
        // 设置默认边框和圆角

//        // 设置默认边框和圆角
//        int arc = 20; // 圆角半径
//        button.setBorder(BorderFactory.createCompoundBorder(
//                new RoundedCornerBorder(arc),
//                BorderFactory.createEmptyBorder(6, 16, 6, 16)
//        ));

        // 添加鼠标悬停效果,离开时恢复原状

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
//                button.setBackground(new Color(52, 152, 219));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(52, 152, 219), 1),
                        BorderFactory.createEmptyBorder(6, 16, 6, 16)
                ));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // 设置默认背景颜色和边框
//                button.setBackground(new Color(41, 185, 163));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(214, 236, 221), 1),
                        BorderFactory.createEmptyBorder(6, 16, 6, 16)
                ));
            }


            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
//                button.setBackground(new Color(36, 113, 163));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(36, 113, 163), 1),
                        BorderFactory.createEmptyBorder(6, 16, 6, 16)
                ));
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                // 设置默认背景颜色和边框
//                button.setBackground(new Color(41, 185, 163));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(214, 236, 221), 1),
                        BorderFactory.createEmptyBorder(6, 16, 6, 16)
                ));
            }
        });

        return button;
    }





    private void handleAskButtonClick(Project project) {
        UIState state = uiStates.get(project);
        if (state == null || state.questionTextArea == null) return;

        String question = state.questionTextArea.getText().trim();
        if (question.isEmpty() || question.equals("输入问题，点击提交按钮发送")) {
            String errorMessage = "<div style='color: #ff6b6b; padding: 10px; background-color: rgba(255, 107, 107, 0.1); border-radius: 4px;'>请输入问题内容</div>";
            updateResult(errorMessage, project);
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            state.askButton.setVisible(false);
            state.cancelButton.setVisible(true);
        });

        state.chatHistory.setLength(0);
        state.messageBuilder.setLength(0);
        state.currentAnswer.setLength(0);

        String qaId = String.valueOf(System.currentTimeMillis());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        String formattedQuestion = String.format(
                "<div class='chat-item question-item' id='qa-%s' style='margin: 10px 0; padding: 10px; border-left: 3px solid #4CAF50; position: relative;'>" +
                        "<div style='display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px;'>" +
                        "<strong style='color: #4CAF50;'>Q:</strong>" +
                        "<div style=\"display: flex; align-items: center; gap: 10px;\">" +
                        "<span style='color: #666; font-size: 0.9em;'>%s</span>" +
                        "<button onclick='handleDeleteQA(\\\"qa-%s\\\")' style='background: none; border: none; color: #999; cursor: pointer;'>×</button>" +
                        "</div>" +
                        "</div>" +
                        "<div style='margin-left: 20px;'>%s</div>" +
                        "</div>",
                qaId, timestamp, qaId, question
        );

        state.chatHistory.append(formattedQuestion);
        state.chatHistory.append("<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>");

        CodeService codeService = new CodeService();
        String prompt = String.format("根据提出的问题作出回答，用中文回答；若需编程，请给出示例，以Java作为默认编程语言输出；问题如下：%s", CODE_UTIL.formatCode(question));

        try {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "处理中", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    state.messageBuilder.setLength(0);
                    state.currentAnswer.setLength(0);
                    state.currentAnswer.append(formattedQuestion);

                    AtomicReference<String> displayContent = new AtomicReference<>();
                    try {
                        if (codeService.generateByStream()) {
                            codeService.generateCommitMessageStream(
                                    prompt,
                                    token -> {
                                        state.messageBuilder.append(token);
                                        String formattedAnswer = String.format(
                                                "<div class='chat-item answer-item' id='answer-%s' style='margin: 10px 0; padding: 10px; border-left: 3px solid #2196F3;'>" +
                                                        "<div style='display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px;'>" +
                                                        "<strong style='color: #2196F3;'>A:</strong>" +
                                                        "<span style='color: #666; font-size: 0.9em;'>%s</span>" +
                                                        "</div>" +
                                                        "<div style='margin-left: 20px;'>%s</div>" +
                                                        "</div>",
                                                qaId, timestamp, state.messageBuilder.toString()
                                        );
                                        displayContent.set(state.currentAnswer +
                                                "<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>" +
                                                formattedAnswer);
                                        updateResult(displayContent.get(), project);
                                    },
                                    error -> ApplicationManager.getApplication().invokeLater(() ->
                                            IdeaDialogUtil.showError(project, "处理失败: " + error.getMessage(), "Error")),
                                    () -> {
                                        saveHistory(project, question, displayContent.get());
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            resetButton(project);
                                            state.questionTextArea.setText("输入问题，点击提交按钮发送");
                                            state.questionTextArea.setForeground(new Color(180, 180, 180));
                                        });
                                    }
                            );
                        }
                    } catch (Exception e) {
                        IdeaDialogUtil.showError(project, "处理失败: " + e.getMessage(), "Error");
                    }
                }
            });
        } catch (Exception e) {
            Messages.showMessageDialog(project, "处理失败: " + e.getMessage(), "Error", Messages.getErrorIcon());
        }
    }

    //     在类中添加ProjectManagerListener
    private final ProjectManagerListener projectListener = new ProjectManagerListener() {
        @Override
        public void projectClosed(@NotNull Project project) {
            // 当项目关闭时，从映射中移除实例
            instances.remove(project);
        }
    };


    //把以下两行抽成一个方法
    public void resetButton(Project project) {
        UIState state = uiStates.get(project);
        if (state != null && state.askButton != null && state.cancelButton != null) {
            state.askButton.setVisible(true);
            state.cancelButton.setVisible(false);
        }
    }

    public void submitButton(Project project) {
        UIState state = uiStates.get(project);
        if (state != null && state.askButton != null && state.cancelButton != null) {
            state.askButton.setVisible(false);
            state.cancelButton.setVisible(true);
        }

    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    // 在CombinedWindowFactory中添加:


    private void saveHistory(Project project, String question, String answer) {
        ChatHistoryService service = ChatHistoryService.getInstance();
        // 只保存问答对，不保存原始Map字符串
        service.addChatRecord(question, answer);
    }

    public static CombinedWindowFactory getInstance(Project project) {
        if (project == null) {
            return null;
        }
        return instances.get(project);
    }

    public CombinedWindowFactory() {
        // 构造函数保持简单
    }


    public void dispose() {
        if (isDisposed) return;
        isDisposed = true;

        for (Map.Entry<Project, UIState> entry : uiStates.entrySet()) {
            disposeUIState(entry.getValue());
        }
        uiStates.clear();

        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
            messageBusConnection = null;
        }

        instances.remove(currentProject);
        currentProject = null;
    }

    private void disposeUIState(UIState state) {
        if (state.markdownViewer != null) {
            state.markdownViewer.dispose();
            state.markdownViewer = null;
        }
        // 可选：清理其他组件资源
    }

    private static class LoadingDots extends JPanel {
        private static final int DOT_COUNT = 3;
        private static final int DOT_SIZE = 8;
        private static final int DOT_SPACING = 12;
        private static final Color DOT_COLOR = new Color(76, 175, 80);
        private int currentDot = 0;
        private final Timer timer;

        public LoadingDots() {
            setOpaque(false);
            setPreferredSize(new Dimension(DOT_COUNT * (DOT_SIZE + DOT_SPACING), DOT_SIZE + 10));
            
            timer = new Timer(300, e -> {
                currentDot = (currentDot + 1) % DOT_COUNT;
                repaint();
            });
            timer.start();
        }

        public void stop() {
            timer.stop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            
            // 启用抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int centerY = getHeight() / 2;
            
            for (int i = 0; i < DOT_COUNT; i++) {
                int x = i * (DOT_SIZE + DOT_SPACING) + DOT_SPACING;
                float alpha = i == currentDot ? 1.0f : 0.3f;
                g2d.setColor(new Color(
                    DOT_COLOR.getRed(),
                    DOT_COLOR.getGreen(),
                    DOT_COLOR.getBlue(),
                    (int)(alpha * 255)
                ));
                g2d.fillOval(x, centerY - DOT_SIZE/2, DOT_SIZE, DOT_SIZE);
            }
            
            g2d.dispose();
        }
    }

    public void startLoadingAnimation(Project project) {
        UIState state = uiStates.get(project);
        if (state != null && state.outputPanel != null) {
            updateResult("",project);
            // 创建加载面板
            JPanel loadingPanel = new JPanel(new BorderLayout());
            loadingPanel.setBackground(ideBackgroundColor);
            
            // 创建动画面板
            JPanel animationPanel = new JPanel(new BorderLayout(15, 0)); // 将垂直间距改为0
            animationPanel.setBackground(ideBackgroundColor);
            animationPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
            
            // 创建加载动画面板
            JPanel loadingAnimationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
            loadingAnimationPanel.setBackground(ideBackgroundColor);
            LoadingDots loadingDots = new LoadingDots();
            loadingAnimationPanel.add(loadingDots);
            
            // 提示文本面板
            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 8));
            textPanel.setBackground(ideBackgroundColor);
            textPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0)); // 将上边距改为2
            
            JLabel titleLabel = new JLabel("正在生成项目文档，" +
                    "请稍候...");
            titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            titleLabel.setForeground(new Color(33, 33, 33));
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
            
//            JLabel subtitleLabel = new JLabel("请稍候，这可能需要一点时间...");
//            subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
//            subtitleLabel.setForeground(new Color(128, 128, 128));
//            subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
            
            textPanel.add(titleLabel);
//            textPanel.add(subtitleLabel);
            
            // 组装组件
            animationPanel.add(loadingAnimationPanel, BorderLayout.NORTH);
            animationPanel.add(textPanel, BorderLayout.CENTER);
            
            loadingPanel.add(animationPanel, BorderLayout.CENTER);
            
            // 替换输出面板内容
            state.outputPanel.removeAll();
            state.outputPanel.add(loadingPanel, BorderLayout.CENTER);
            state.outputPanel.revalidate();
            state.outputPanel.repaint();
            
            // 保存引用
            state.loadingPanel = loadingPanel;
            state.loadingTimer = null;
        }
    }

    public void stopLoadingAnimation(Project project) {
        UIState state = uiStates.get(project);
        if (state != null && state.outputPanel != null) {
            // 停止动画
            Component[] components = state.loadingPanel.getComponents();
            if (components.length > 0 && components[0] instanceof JPanel) {
                JPanel animationPanel = (JPanel) components[0];
                Component[] animationComponents = animationPanel.getComponents();
                for (Component comp : animationComponents) {
                    if (comp instanceof JPanel) {
                        JPanel loadingAnimationPanel = (JPanel) comp;
                        Component[] loadingComponents = loadingAnimationPanel.getComponents();
                        for (Component loadingComp : loadingComponents) {
                            if (loadingComp instanceof LoadingDots) {
                                ((LoadingDots) loadingComp).stop();
                            }
                        }
                    }
                }
            }
            
            // 淡出动画
            Timer fadeOutTimer = new Timer(50, new ActionListener() {
                private float opacity = 1.0f;
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    opacity -= 0.1f;
                    if (opacity <= 0) {
                        ((Timer)e.getSource()).stop();
                        state.outputPanel.removeAll();
                        state.outputPanel.add(state.markdownViewer.getComponent(), BorderLayout.CENTER);
                        state.outputPanel.revalidate();
                        state.outputPanel.repaint();
                        state.loadingPanel = null;
                    } else {
                        state.outputPanel.setBackground(new Color(
                            ideBackgroundColor.getRed(),
                            ideBackgroundColor.getGreen(),
                            ideBackgroundColor.getBlue(),
                            (int)(opacity * 255)
                        ));
                        state.outputPanel.repaint();
                    }
                }
            });
            fadeOutTimer.start();
        }
    }


    public void updateLoadingProgress(Project project, String message) {
        UIState state = uiStates.get(project);
        if (state != null && state.loadingPanel != null) {
            // 更新进度条文本
            Component[] components = state.loadingPanel.getComponents();
            if (components.length > 0 && components[0] instanceof JPanel) {
                JPanel animationPanel = (JPanel) components[0];
                Component[] animationComponents = animationPanel.getComponents();
                for (Component comp : animationComponents) {
                    if (comp instanceof JPanel) {
                        JPanel progressContainer = (JPanel) comp;
                        Component[] progressComponents = progressContainer.getComponents();
                        for (Component progressComp : progressComponents) {
                            if (progressComp instanceof JProgressBar) {
                                JProgressBar progressBar = (JProgressBar) progressComp;
                                progressBar.setString(message);
                                progressBar.setIndeterminate(true);
                                progressBar.setEnabled(true);
                                progressBar.setVisible(true);
                                break;
                            }
                        }
                    }
                }
            }
            state.outputPanel.revalidate();
            state.outputPanel.repaint();
        }
    }

//    public void stopLoadingAnimation(Project project) {
//        UIState state = uiStates.get(project);
//        if (state != null && state.outputPanel != null) {
//            // 停止并清理定时器
//            if (state.loadingTimer != null) {
//                state.loadingTimer.stop();
//                state.loadingTimer = null;
//            }
//
//            // 添加淡出效果
//            Timer fadeTimer = new Timer(50, new ActionListener() {
//                private float opacity = 1.0f;
//
//                @Override
//                public void actionPerformed(ActionEvent e) {
//                    opacity -= 0.1f;
//                    if (opacity <= 0) {
//                        ((Timer)e.getSource()).stop();
//                        state.outputPanel.removeAll();
//                        state.outputPanel.add(state.markdownViewer.getComponent(), BorderLayout.CENTER);
//                        state.outputPanel.revalidate();
//                        state.outputPanel.repaint();
//                        state.loadingPanel = null;
//                    } else {
//                        state.outputPanel.setBackground(new Color(
//                            ideBackgroundColor.getRed(),
//                            ideBackgroundColor.getGreen(),
//                            ideBackgroundColor.getBlue(),
//                            (int)(opacity * 255)
//                        ));
//                        state.outputPanel.repaint();
//                    }
//                }
//            });
//            fadeTimer.start();
//        }
//    }

    private void toggleHistoryView(Project project) {
        UIState state = uiStates.get(project);
        if (state == null) return;

        if (state.isHistoryView) {
            // Switch back to chat view
            state.outputPanel.removeAll();
            state.outputPanel.add(state.markdownViewer.getComponent(), BorderLayout.CENTER);
            state.isHistoryView = false;
        } else {
            // Switch to history view
            JPanel historyPanel = new JPanel(new BorderLayout());
            historyPanel.setBackground(ideBackgroundColor);

            // Create history list
            state.historyListModel = new DefaultListModel<>();
            JList<HistoryItem> historyList = new JList<>(state.historyListModel);
            historyList.setCellRenderer(new HistoryListCellRenderer());
            historyList.setBackground(ideBackgroundColor);
            historyList.setForeground(ideFontColor);

            // Load history
            ChatHistoryService service = ChatHistoryService.getInstance();
            Map<String, String> history = service.getChatHistory();
            for (Map.Entry<String, String> entry : history.entrySet()) {
                state.historyListModel.addElement(new HistoryItem(entry.getKey(), 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
            }

            historyPanel.add(new JBScrollPane(historyList), BorderLayout.CENTER);
            state.outputPanel.removeAll();
            state.outputPanel.add(historyPanel, BorderLayout.CENTER);
            state.isHistoryView = true;
        }

        state.outputPanel.revalidate();
        state.outputPanel.repaint();
    }

}