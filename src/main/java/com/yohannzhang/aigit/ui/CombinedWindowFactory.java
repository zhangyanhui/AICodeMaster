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
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import com.yohannzhang.aigit.util.OpenAIUtil;
import org.jetbrains.annotations.NotNull;

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
    private final Map<Project, UIState> uiStates = Collections.synchronizedMap(new HashMap<>());

    private static class UIState {
        JTextArea questionTextArea;
        JButton askButton;
        JButton cancelButton;
        JComboBox<String> modelComboBox;
        JComboBox<String> modelSelectComboBox;
        JPanel outputPanel;
        JBCefBrowser markdownViewer;
        StringBuilder chatHistory = new StringBuilder();
        StringBuilder currentAnswer = new StringBuilder();
        boolean isHistoryView = false;
        JPanel historyPanel;
        DefaultListModel<HistoryItem> historyListModel;
        private final StringBuilder messageBuilder = new StringBuilder();

    }

    // Add new field for chat history
//    private final StringBuilder chatHistory = new StringBuilder();

//    private final StringBuilder currentQAContent = new StringBuilder();

//    private StringBuilder currentAnswer = new StringBuilder(); // 添加新字段来跟踪当前答案

    // Add field for history panel
//    private JPanel historyPanel;
//    private JList<HistoryItem> historyList;
//    private DefaultListModel<HistoryItem> historyListModel;
    private boolean isHistoryView = false;

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
        if (isDisposed) return;

//        this.currentProject = project;

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

        // 添加历史记录图标
        AnAction historyAction = new AnAction("Show Chat History", "Show chat history", AllIcons.Vcs.History) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
//                toggleHistoryView(project);
            }
        };

        toolWindow.setTitleActions(Collections.singletonList(historyAction));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultConstraints();

        panel.add(createOutputPanel(project), gbc);
        gbc.gridy = 1;
        gbc.weighty = 0.1;
        panel.add(createInputPanel(project), gbc);

        Content content = toolWindow.getContentManager().getFactory().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        // 快捷键
        historyAction.registerCustomShortcutSet(
                new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK)),
                panel);

        // 刷新 UI
        ApplicationManager.getApplication().invokeLater(() -> {
            refreshUIOnThemeChange(project);
            UIState state = uiStates.get(project);
            if (state != null && state.questionTextArea != null) {
                state.questionTextArea.setForeground(new Color(180, 180, 180));
            }
        });

        instances.put(project, this);
    }
//    public void showWelcomePage() {
//        String welcomeHtml = readResourceFile("welcome.html");
//        markdownViewer.loadHTML(welcomeHtml);
//        isHistoryView = false; // 确保不在历史视图
//
//        // 刷新UI确保显示正确
//        outputPanel.removeAll();
//        outputPanel.add(markdownViewer.getComponent(), BorderLayout.CENTER);
//        outputPanel.revalidate();
//        outputPanel.repaint();
//    }

    private void onThemeChanged(EditorColorsScheme scheme) {
        ApplicationManager.getApplication().invokeLater(() -> {
            initIdeBackgroundColor(); // 更新主题色
            refreshUIOnThemeChange(currentProject); // 刷新 UI 样式
        });
    }

    @Override
    public void globalSchemeChange(EditorColorsScheme scheme) {
        onThemeChanged(scheme);
    }

    private void refreshUIOnThemeChange(Project project) {
        UIState state = uiStates.get(project);
        if (state == null) return;

        if (state.questionTextArea != null) {
            if (!state.questionTextArea.getText().equals("输入问题，点击提交按钮发送")) {
                state.questionTextArea.setForeground(ideFontColor);
            } else {
                state.questionTextArea.setForeground(new Color(180, 180, 180));
            }
            state.questionTextArea.setBackground(ideBackgroundColor);
        }

        if (state.outputPanel != null) {
            state.outputPanel.setBackground(ideBackgroundColor);
            state.outputPanel.setForeground(ideFontColor);
            state.outputPanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                    "AI 助手"
            ));
        }

        if (state.askButton != null && state.cancelButton != null) {
            state.askButton.setBackground(BUTTON_COLOR);
            state.askButton.setForeground(ideFontColor);
            state.cancelButton.setBackground(BUTTON_COLOR);
            state.cancelButton.setForeground(ideFontColor);
        }

        if (state.modelComboBox != null) {
            state.modelComboBox.setBackground(ideBackgroundColor);
            state.modelComboBox.setForeground(ideFontColor);
        }

        if (state.modelSelectComboBox != null) {
            state.modelSelectComboBox.setBackground(ideBackgroundColor);
            state.modelSelectComboBox.setForeground(ideFontColor);
        }

        Component[] components = state.questionTextArea.getParent().getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                comp.setBackground(ideBackgroundColor);
                refreshComponentBackground(comp);
            }
        }

        if (state.markdownViewer != null) {
            String script = "document.documentElement.style.setProperty('--font-size', '" + fontSize + "px');" +
                    "document.documentElement.style.setProperty('--workspace-color', '" + toHex(ideBackgroundColor) + "');" +
                    "document.documentElement.style.setProperty('--idefont-color', '" + toHex(ideFontColor) + "');";
            ApplicationManager.getApplication().invokeLater(() -> {
                state.markdownViewer.getCefBrowser().executeJavaScript(script, "about:blank", 0);
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
        fontSize = colorsScheme.getEditorFontSize2D();

        // 计算亮度
        double brightness = (0.299 * ideBackgroundColor.getRed() +
                0.587 * ideBackgroundColor.getGreen() +
                0.114 * ideBackgroundColor.getBlue()) / 255;

        // 动态调整前景色
        ideFontColor = brightness < 0.5 ?
                new Color(224, 224, 224) :  // 淡白色
                Color.BLACK;

        // 确保背景色有足够对比度
        if (brightness < 0.5) {
            ideBackgroundColor = new Color(Math.max(ideBackgroundColor.getRed(), 43),
                    Math.max(ideBackgroundColor.getGreen(), 43),
                    Math.max(ideBackgroundColor.getBlue(), 43));
        } else {
            ideBackgroundColor = new Color(Math.min(ideBackgroundColor.getRed(), 250),
                    Math.min(ideBackgroundColor.getGreen(), 250),
                    Math.min(ideBackgroundColor.getBlue(), 250));
        }
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
        outputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                "AI 助手"
        ));

        state.markdownViewer = JBCefBrowser.createBuilder()
                .setUrl("about:blank")
                .build();
        state.markdownViewer.getComponent().setBorder(BorderFactory.createEmptyBorder());

        String welcomeHtml = readResourceFile("empty.html");
        String script = "document.documentElement.style.setProperty('--font-size', '" + fontSize + "px');" +
                "document.documentElement.style.setProperty('--workspace-color', '" + toHex(ideBackgroundColor) + "');" +
                "document.documentElement.style.setProperty('--idefont-color', '" + toHex(ideFontColor) + "');";

        ApplicationManager.getApplication().invokeLater(() -> {
            state.markdownViewer.loadHTML(welcomeHtml);
            state.markdownViewer.getCefBrowser().executeJavaScript(script, "about:blank", 0);
        });

        outputPanel.add(state.markdownViewer.getComponent(), BorderLayout.CENTER);
        state.outputPanel = outputPanel;

        return outputPanel;
    }

//    private void toggleHistoryView() {
//        if (!isHistoryView) {
//            // 显示历史面板
//            if (historyPanel == null) {
//                createHistoryPanel();
//            }
//            outputPanel.remove(markdownViewer.getComponent());
//            outputPanel.add(historyPanel, BorderLayout.CENTER);
//            updateHistoryList();
//        } else {
//            // 显示主内容
//            outputPanel.remove(historyPanel);
//            outputPanel.add(markdownViewer.getComponent(), BorderLayout.CENTER);
//            if (chatHistory.length() == 0) {

    /// /                showWelcomeContent();
//            }
//        }
//        isHistoryView = !isHistoryView;
//        outputPanel.revalidate();
//        outputPanel.repaint();
//    }


//    private void createHistoryPanel() {
//        historyPanel = new JPanel(new BorderLayout());
//        historyPanel.setBackground(ideBackgroundColor);
//        historyPanel.setBorder(BorderFactory.createCompoundBorder(
//                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
//                BorderFactory.createEmptyBorder(10, 10, 10, 10)
//        ));
//
//        historyListModel = new DefaultListModel<>();
//        historyList = new JList<>(historyListModel);
//        historyList.setBackground(ideBackgroundColor);
//        historyList.setForeground(ideFontColor);
//        historyList.setFont(new Font("SansSerif", Font.PLAIN, 14));
//        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        historyList.setFixedCellHeight(60);
//        historyList.setCellRenderer(new HistoryListCellRenderer());
//
//        historyList.addListSelectionListener(e -> {
//            if (!e.getValueIsAdjusting()) {
//                int selectedIndex = historyList.getSelectedIndex();
//                if (selectedIndex != -1) {
//                    String[] historyItems = chatHistory.toString().split(
//                            "<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>"
//                    );
//
//                    if (selectedIndex < historyItems.length) {
//                        String questionItem = historyItems[selectedIndex];
//                        String answerItem = (selectedIndex + 1 < historyItems.length) ?
//                                historyItems[selectedIndex + 1] : "";
//
//                        String fullContent = questionItem +
//                                "<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>" +
//                                answerItem;
//
//                        updateResult(fullContent);
//
//                        if (isHistoryView) {
//                            toggleHistoryView(pro);
//                        }
//                    }
//                }
//            }
//        });
//
//        JScrollPane listScrollPane = new JBScrollPane(historyList);
//        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
//        historyPanel.add(listScrollPane, BorderLayout.CENTER);
//    }
//
//    private void updateHistoryList() {
//        historyListModel.clear();
//        String[] historyItems = chatHistory.toString().split("<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>");
//
//        for (String item : historyItems) {
//            if (!item.trim().isEmpty()) {
//                // 提取问题和时间戳
//                int qStart = item.indexOf("<strong style='color: #4CAF50;'>Q:</strong>");
//                if (qStart != -1) {
//                    int qEnd = item.indexOf("</div>", qStart);
//                    if (qEnd != -1) {
//                        String question = item.substring(qStart + 40, qEnd).trim();
//                        String timestamp = "";
//                        int timeStart = item.indexOf("<span style='color: #666; font-size: 0.9em;'>");
//                        if (timeStart != -1) {
//                            int timeEnd = item.indexOf("</span>", timeStart);
//                            if (timeEnd != -1) {
//                                timestamp = item.substring(timeStart + 45, timeEnd);
//                            }
//                        }
//                        historyListModel.addElement(new HistoryItem(question, timestamp));
//                    }
//                }
//            }
//        }
//    }

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
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBackground(ideBackgroundColor);

        String[] clientArr = ApiKeySettings.getInstance().getAvailableModels();
        JComboBox<String> modelComboBox = new JComboBox<>(clientArr);
        modelComboBox.setSelectedItem(ApiKeySettings.getInstance().getSelectedModule());
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

        questionTextArea.setEditable(true);

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
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 设置按钮的 preferredSize
        button.setPreferredSize(new Dimension(80, 32));

        // 设置默认背景颜色和边框
        button.setBackground(new Color(41, 128, 185));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 128, 185), 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));

        // 添加鼠标悬停效果
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(new Color(52, 152, 219));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(52, 152, 219), 1),
                        BorderFactory.createEmptyBorder(6, 16, 6, 16)
                ));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(new Color(41, 128, 185));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(41, 128, 185), 1),
                        BorderFactory.createEmptyBorder(6, 16, 6, 16)
                ));
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                button.setBackground(new Color(36, 113, 163));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(36, 113, 163), 1),
                        BorderFactory.createEmptyBorder(6, 16, 6, 16)
                ));
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                button.setBackground(new Color(41, 128, 185));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(41, 128, 185), 1),
                        BorderFactory.createEmptyBorder(6, 16, 6, 16)
                ));
            }
        });

        return button;
    }

    private String generateQaId() {
        return String.valueOf(System.currentTimeMillis());
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

    // 在类中添加ProjectManagerListener
    private final ProjectManagerListener projectListener = new ProjectManagerListener() {
        @Override
        public void projectOpened(@NotNull Project project) {
            // 当项目打开时，将实例添加到映射中
            instances.put(project, CombinedWindowFactory.this);
        }

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

    // 添加一个方法来检查实例是否有效
    public boolean isValid() {
        return !isDisposed && currentProject != null && !currentProject.isDisposed();
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

}