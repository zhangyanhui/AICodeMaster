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
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.content.Content;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.messages.MessageBusConnection;
import com.vladsch.flexmark.ext.tables.TablesExtension;
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
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class CombinedWindowFactory implements ToolWindowFactory, EditorColorsListener {
    private static final Map<Project, CombinedWindowFactory> instances = Collections.synchronizedMap(new HashMap<>());
    private MessageBusConnection messageBusConnection;
    private static final CodeUtil CODE_UTIL = new CodeUtil();
    private static final Font BUTTON_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Color BUTTON_COLOR = new Color(32, 86, 105);
    private static final Color BUTTON_HOVER_COLOR = new Color(143, 0, 200);
    private static final int MAX_CONVERSATION_CONTEXT_TURNS = 6;
    private static final String QUESTION_PLACEHOLDER = "向 DevPilot 提问，Enter 发送，Alt+Enter 换行";

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
        List<ConversationTurn> conversationTurns = new ArrayList<>();
        boolean isHistoryView = false;
        JPanel historyPanel;
        DefaultListModel<HistoryItem> historyListModel;
        private final StringBuilder messageBuilder = new StringBuilder();
        Timer loadingTimer;
        JPanel loadingPanel;
        // 上下文文件选择相关组件
        JPanel contextPanel;
        JButton selectFileButton;
        JList<String> selectedFilesList;
        DefaultListModel<String> selectedFilesModel;
        java.util.List<String> selectedFilesPaths = new java.util.ArrayList<>();
        java.util.Map<String, String> fileContentsCache = new java.util.HashMap<>();
        // 项目文件搜索相关
        java.util.List<VirtualFile> projectFiles = new java.util.ArrayList<>();
        DefaultListModel<VirtualFile> searchResultsModel = new DefaultListModel<>();
        // 界面组件引用（用于更新显示）
        JLabel fileCountLabel;
        JBScrollPane fileListScrollPane;
    }

    private static class ConversationTurn {
        private final String question;
        private final String answer;

        private ConversationTurn(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
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
            applyQuestionTextAreaColors(state.questionTextArea, !isQuestionPlaceholder(state.questionTextArea.getText()));
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

    private Color subtleFill(int alpha) {
        return blendWithIdeBackground(new Color(127, 127, 127), alpha);
    }

    private Color subtleLine(int alpha) {
        return blendWithIdeBackground(new Color(127, 127, 127), alpha);
    }

    private Color accentFill(Color color, int alpha) {
        return blendWithIdeBackground(color, alpha);
    }

    private Color blendWithIdeBackground(Color overlay, int alpha) {
        Color background = ideBackgroundColor != null ? ideBackgroundColor : Color.WHITE;
        double ratio = Math.max(0, Math.min(255, alpha)) / 255.0;
        int red = (int) Math.round(overlay.getRed() * ratio + background.getRed() * (1.0 - ratio));
        int green = (int) Math.round(overlay.getGreen() * ratio + background.getGreen() * (1.0 - ratio));
        int blue = (int) Math.round(overlay.getBlue() * ratio + background.getBlue() * (1.0 - ratio));
        return new Color(red, green, blue);
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
        state.markdownViewer.getComponent().setBackground(ideBackgroundColor);
        state.markdownViewer.getComponent().setForeground(ideFontColor);

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

    private static class HistoryListCellRenderer extends JPanel implements ListCellRenderer<HistoryItem> {
        private final JLabel qLabel = new JLabel("Q:");
        private final JLabel questionLabel = new JLabel();
        private final JLabel timeLabel = new JLabel();
        private final JPanel leftPanel = new JPanel(new BorderLayout(5, 0));

        private HistoryListCellRenderer() {
            super(new BorderLayout(5, 5));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            qLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            qLabel.setForeground(new Color(76, 175, 80));
            qLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

            questionLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
            timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

            leftPanel.setOpaque(true);
            leftPanel.add(qLabel, BorderLayout.WEST);
            leftPanel.add(questionLabel, BorderLayout.CENTER);

            add(leftPanel, BorderLayout.CENTER);
            add(timeLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends HistoryItem> list, HistoryItem value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            Color background = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color foreground = isSelected ? list.getSelectionForeground() : list.getForeground();

            setBackground(background);
            leftPanel.setBackground(background);
            questionLabel.setForeground(foreground);
            timeLabel.setForeground(isSelected ? foreground : new Color(128, 128, 128));

            if (value != null) {
                questionLabel.setText(value.question);
                timeLabel.setText(value.timestamp);
            } else {
                questionLabel.setText("");
                timeLabel.setText("");
            }
            return this;
        }
    }

    private JPanel createInputPanel(Project project) {
        UIState state = getOrCreateState(project);
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(ideBackgroundColor);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        // 创建统一的输入容器
        JPanel unifiedInputContainer = new JPanel(new BorderLayout());
        unifiedInputContainer.setBackground(subtleFill(10));
        unifiedInputContainer.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(subtleLine(78), 1, true),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)
        ));

        // 创建上下文文件区域（放在顶部）
        JPanel contextFileArea = createIntegratedContextFilePanel(project);
        
        // 创建文本输入区域
        JTextArea questionTextArea = new JTextArea(3, 50);
        questionTextArea.setLineWrap(true);
        questionTextArea.setWrapStyleWord(true);
        questionTextArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        questionTextArea.setBorder(BorderFactory.createEmptyBorder(6, 2, 8, 2));
        questionTextArea.setOpaque(true);
        questionTextArea.getCaret().setBlinkRate(0);
        if (questionTextArea.getCaret() instanceof DefaultCaret caret) {
            caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        }

        String placeholderText = QUESTION_PLACEHOLDER;
        questionTextArea.setText(placeholderText);
        applyQuestionTextAreaColors(questionTextArea, false);

        questionTextArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (isQuestionPlaceholder(questionTextArea.getText())) {
                    questionTextArea.setText("");
                }
                applyQuestionTextAreaColors(questionTextArea, true);
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (questionTextArea.getText() == null || questionTextArea.getText().trim().isEmpty()) {
                    questionTextArea.setText(placeholderText);
                    applyQuestionTextAreaColors(questionTextArea, false);
                } else {
                    applyQuestionTextAreaColors(questionTextArea, true);
                }
            }
        });

        // 添加鼠标点击监听器作为额外保障
        questionTextArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isQuestionPlaceholder(questionTextArea.getText())) {
                    questionTextArea.setText("");
                }
                applyQuestionTextAreaColors(questionTextArea, true);
            }
        });

        // 添加键盘事件监听器：回车发送，Alt+回车换行
        questionTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isAltDown()) {
                        // Alt+回车：插入换行符
                        int caretPosition = questionTextArea.getCaretPosition();
                        String currentText = questionTextArea.getText();
                        String newText = currentText.substring(0, caretPosition) + "\n" + currentText.substring(caretPosition);
                        questionTextArea.setText(newText);
                        questionTextArea.setCaretPosition(caretPosition + 1);
                        e.consume(); // 阻止默认行为
                    } else {
                        // 单独回车：发送消息
                        e.consume(); // 阻止默认换行行为
                        handleAskButtonClick(project);
                    }
                }
            }
        });

        questionTextArea.setEditable(true);

        // 创建底部控制栏（模型选择 + 按钮）
        JPanel bottomControlPanel = new JPanel(new BorderLayout());
        bottomControlPanel.setBackground(subtleFill(0));
        bottomControlPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, subtleLine(38)),
                BorderFactory.createEmptyBorder(7, 0, 0, 0)
        ));

        // 左侧：模型选择区域
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modelPanel.setBackground(subtleFill(0));

        String[] clientArr = ApiKeySettings.getInstance().getAvailableModels();
        JComboBox<String> modelComboBox = new JComboBox<>(clientArr);
        modelComboBox.setSelectedItem(ApiKeySettings.getInstance().getSelectedClient());
        modelComboBox.setPreferredSize(new Dimension(118, 26));
        modelComboBox.setFont(new Font("SansSerif", Font.PLAIN, 11));

        String[] modelArr = ApiKeySettings.getInstance().getModulesForClient((String) modelComboBox.getSelectedItem());
        JComboBox<String> modelSelectComboBox = new JComboBox<>(modelArr);
        modelSelectComboBox.setEditable(true);
        modelSelectComboBox.setSelectedItem(ApiKeySettings.getInstance().getSelectedModule());
        modelSelectComboBox.setPreferredSize(new Dimension(160, 26));
        modelSelectComboBox.setFont(new Font("SansSerif", Font.PLAIN, 11));
        applyComboBoxColors(modelComboBox);
        applyComboBoxColors(modelSelectComboBox);

        modelComboBox.addActionListener(e -> {
            String selectedClient = (String) modelComboBox.getSelectedItem();
            String[] arr = ApiKeySettings.getInstance().getModulesForClient(selectedClient);
            modelSelectComboBox.removeAllItems();
            for (String module : arr) {
                modelSelectComboBox.addItem(module);
            }
            ApiKeySettings.getInstance().setSelectedClient(selectedClient);
        });

        modelSelectComboBox.addActionListener(e -> {
            String selectedModel = (String) modelSelectComboBox.getSelectedItem();
            ApiKeySettings.getInstance().setSelectedModule(selectedModel);
            ApiKeySettings.getInstance().addCustomModule((String) modelComboBox.getSelectedItem(), selectedModel);
        });

        modelPanel.add(modelComboBox);
        modelPanel.add(modelSelectComboBox);

        // 右侧：按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setBackground(subtleFill(0));

        JButton askButton = createIconActionButton("/icons/fasong.png", "发送", ButtonStyle.PRIMARY);
        JButton cancelButton = createActionButton("停止", ButtonStyle.DANGER);
        cancelButton.setVisible(false);

        askButton.addActionListener(e -> handleAskButtonClick(project));
        cancelButton.addActionListener(e -> handleCancel(project));

        buttonPanel.add(cancelButton);

        // 组装底部控制栏
        bottomControlPanel.add(modelPanel, BorderLayout.WEST);
        bottomControlPanel.add(buttonPanel, BorderLayout.EAST);

        // 创建文本区域容器，使用BorderLayout替代OverlayLayout
        JPanel textContainer = new JPanel(new BorderLayout());
        textContainer.setBackground(subtleFill(0));

        // 添加滚动面板
        JBScrollPane scrollPane = new JBScrollPane(questionTextArea);
        scrollPane.setBorder(null);
        scrollPane.setBackground(subtleFill(0));
        scrollPane.getViewport().setBackground(subtleFill(0));
        scrollPane.setOpaque(true);
        scrollPane.getViewport().setOpaque(true);

        // 添加组件到容器
        textContainer.add(scrollPane, BorderLayout.CENTER);
        textContainer.add(bottomControlPanel, BorderLayout.SOUTH);

        // 组装统一输入容器
        unifiedInputContainer.add(contextFileArea, BorderLayout.NORTH);
        unifiedInputContainer.add(textContainer, BorderLayout.CENTER);
        
        // 最终组装到主面板
        inputPanel.add(unifiedInputContainer, BorderLayout.CENTER);

        // 保存到状态
        state.modelComboBox = modelComboBox;
        state.modelSelectComboBox = modelSelectComboBox;
        state.askButton = askButton;
        state.cancelButton = cancelButton;
        state.questionTextArea = questionTextArea;

        return inputPanel;
    }

    private boolean isQuestionPlaceholder(String text) {
        return text != null && text.trim().equals(QUESTION_PLACEHOLDER.trim());
    }

    private void applyQuestionTextAreaColors(JTextArea textArea, boolean editing) {
        textArea.setBackground(subtleFill(0));
        textArea.setForeground(editing ? getReadableTextColor() : new Color(180, 180, 180));
        textArea.setCaretColor(getStableCaretColor());
        textArea.setSelectedTextColor(getSelectionForegroundColor());
        textArea.setSelectionColor(getSelectionBackgroundColor());
        textArea.setOpaque(true);
        textArea.getCaret().setBlinkRate(0);
        textArea.getCaret().setVisible(textArea.hasFocus());
    }

    private void applyComboBoxColors(JComboBox<String> comboBox) {
        Color background = subtleFill(18);
        Color foreground = getReadableTextColor();
        comboBox.setBackground(background);
        comboBox.setForeground(foreground);
        comboBox.setOpaque(true);

        Component editorComponent = comboBox.getEditor().getEditorComponent();
        if (editorComponent != null) {
            editorComponent.setBackground(background);
            editorComponent.setForeground(foreground);
            if (editorComponent instanceof JTextField textField) {
                textField.setCaretColor(getStableCaretColor());
                textField.setSelectedTextColor(getSelectionForegroundColor());
                textField.setSelectionColor(getSelectionBackgroundColor());
                textField.setOpaque(true);
                textField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            }
        }
    }

    private Color getSelectionBackgroundColor() {
        return isDark(ideBackgroundColor) ? new Color(55, 92, 135) : new Color(184, 207, 229);
    }

    private Color getSelectionForegroundColor() {
        return isDark(ideBackgroundColor) ? Color.WHITE : Color.BLACK;
    }

    private Color getReadableTextColor() {
        return hasUsableContrast(ideFontColor, ideBackgroundColor) ? ideFontColor : getStableCaretColor();
    }

    private Color getStableCaretColor() {
        return isDark(ideBackgroundColor) ? new Color(235, 235, 235) : new Color(32, 32, 32);
    }

    private boolean hasUsableContrast(Color foreground, Color background) {
        if (foreground == null || background == null) {
            return false;
        }
        return Math.abs(luminance(foreground) - luminance(background)) >= 80;
    }

    private boolean isDark(Color color) {
        return color != null && luminance(color) < 128;
    }

    private int luminance(Color color) {
        return (int) Math.round(color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114);
    }

    private enum ButtonStyle {
        PRIMARY,
        SECONDARY,
        DANGER
    }

    private JButton createActionButton(String text, ButtonStyle style) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(text.length() > 2 ? 84 : 58, 28));
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        applyButtonStyle(button, style, false);

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                applyButtonStyle(button, style, true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                applyButtonStyle(button, style, false);
            }
        });

        return button;
    }

    private JButton createIconActionButton(String iconPath, String tooltip, ButtonStyle style) {
        JButton button = createActionButton("", style);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(34, 28));
        button.setText(null);

        java.net.URL iconUrl = getClass().getResource(iconPath);
        if (iconUrl != null) {
            ImageIcon sourceIcon = new ImageIcon(iconUrl);
            Image scaledImage = sourceIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            button.setIcon(new ImageIcon(scaledImage));
        } else {
            button.setText(tooltip);
        }

        return button;
    }

    private void applyButtonStyle(JButton button, ButtonStyle style, boolean hovered) {
        Color borderColor;
        Color backgroundColor;
        Color foregroundColor;

        switch (style) {
            case PRIMARY -> {
                borderColor = hovered ? new Color(72, 134, 237, 160) : new Color(72, 134, 237, 130);
                backgroundColor = hovered ? new Color(54, 117, 214) : new Color(72, 134, 237);
                foregroundColor = Color.WHITE;
            }
            case DANGER -> {
                borderColor = hovered ? accentFill(new Color(215, 58, 73), 135) : subtleLine(70);
                backgroundColor = hovered ? accentFill(new Color(215, 58, 73), 28) : subtleFill(18);
                foregroundColor = hovered ? new Color(215, 58, 73) : ideFontColor;
            }
            default -> {
                borderColor = hovered ? accentFill(new Color(72, 134, 237), 120) : subtleLine(70);
                backgroundColor = hovered ? accentFill(new Color(72, 134, 237), 20) : subtleFill(18);
                foregroundColor = ideFontColor;
            }
        }

        button.setForeground(foregroundColor);
        button.setBackground(backgroundColor);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
    }
    
    /**
     * 创建紧凑的文件操作按钮
     */
    private JButton createCompactFileButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.PLAIN, 12));
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(text.equals("选择文件") ? 76 : 48, 24));
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setContentAreaFilled(true);
        button.setForeground(text.equals("清空") ? new Color(150, 150, 150) : ideFontColor);
        button.setBackground(subtleFill(14));
        button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(subtleLine(62), 1, true),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)
        ));
        
        // 添加悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(new Color(72, 134, 237));
                button.setBackground(accentFill(new Color(72, 134, 237), 20));
                button.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(accentFill(new Color(72, 134, 237), 130), 1, true),
                        BorderFactory.createEmptyBorder(2, 10, 2, 10)
                ));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (text.equals("清空")) {
                    button.setForeground(new Color(135, 135, 135));
                } else {
                    button.setForeground(ideFontColor);
                }
                button.setBackground(subtleFill(14));
                button.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(subtleLine(62), 1, true),
                        BorderFactory.createEmptyBorder(2, 10, 2, 10)
                ));
            }
        });

        return button;
    }
    
    /**
     * 更新文件显示状态
     */
    private void updateFileDisplay(UIState state, JLabel fileCountLabel, JBScrollPane fileListScrollPane) {
        int fileCount = state.selectedFilesPaths.size();
        if (fileCount == 0) {
            fileCountLabel.setText("未选择文件");
            fileListScrollPane.setVisible(false);
            fileListScrollPane.setPreferredSize(new Dimension(0, 0));
        } else {
            fileCountLabel.setText("已选择 " + fileCount + " 个文件");
            fileListScrollPane.setVisible(true);
            // 动态调整高度，最多显示3行
            int displayHeight = Math.min(fileCount * 20 + 6, 66);
            fileListScrollPane.setPreferredSize(new Dimension(0, displayHeight));
        }
        // 刷新布局
        SwingUtilities.invokeLater(() -> {
            if (state.contextPanel != null) {
                state.contextPanel.revalidate();
                state.contextPanel.repaint();
            }
        });
    }

    /**
     * 创建集成到输入框内的上下文文件面板
     */
    private JPanel createIntegratedContextFilePanel(Project project) {
        UIState state = getOrCreateState(project);
        
        JPanel contextPanel = new JPanel(new BorderLayout(0, 0));
        contextPanel.setBackground(subtleFill(0));
        contextPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));

        // 创建顶部操作栏（选择按钮 + 文件计数）
        JPanel topActionPanel = new JPanel(new BorderLayout());
        topActionPanel.setBackground(subtleFill(0));
        
        // 左侧：文件操作按钮
        JPanel actionButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actionButtonPanel.setBackground(subtleFill(0));
        
        JButton selectFileButton = createCompactFileButton("选择文件");
        JButton clearButton = createCompactFileButton("清空");
        clearButton.setForeground(new Color(180, 180, 180));
        
        actionButtonPanel.add(selectFileButton);
        actionButtonPanel.add(Box.createHorizontalStrut(6));
        actionButtonPanel.add(clearButton);
        
        // 右侧：文件计数显示
        JLabel fileCountLabel = new JLabel("未选择文件");
        fileCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        fileCountLabel.setForeground(new Color(145, 145, 145));
        
        topActionPanel.add(actionButtonPanel, BorderLayout.WEST);
        topActionPanel.add(fileCountLabel, BorderLayout.EAST);
        
        // 创建文件列表区域（只在有文件时显示）
        state.selectedFilesModel = new DefaultListModel<>();
        state.selectedFilesList = new JList<>(state.selectedFilesModel);
        state.selectedFilesList.setBackground(ideBackgroundColor);
        state.selectedFilesList.setForeground(ideFontColor);
        state.selectedFilesList.setFont(new Font("SansSerif", Font.PLAIN, 11));
        state.selectedFilesList.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        state.selectedFilesList.setFixedCellHeight(20);
        
        // 设置列表渲染器 - 带删除按钮的自定义渲染器
        state.selectedFilesList.setCellRenderer(new FileListCellRenderer(project));
        
        // 添加鼠标点击监听器处理删除按钮点击
        state.selectedFilesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = state.selectedFilesList.locationToIndex(e.getPoint());
                if (index >= 0 && index < state.selectedFilesModel.getSize()) {
                    Rectangle cellBounds = state.selectedFilesList.getCellBounds(index, index);
                    if (cellBounds != null) {
                        // 检查是否点击了删除按钮区域（右侧22px区域）
                        int relativeX = e.getX() - cellBounds.x;
                        int cellWidth = cellBounds.width;
                        if (relativeX > cellWidth - 22 && relativeX < cellWidth) {
                            // 点击了删除按钮
                            String filePath = state.selectedFilesModel.getElementAt(index);
                            state.selectedFilesModel.removeElement(filePath);
                            state.selectedFilesPaths.remove(filePath);
                            state.fileContentsCache.remove(filePath);
                            updateFileDisplay(state, state.fileCountLabel, state.fileListScrollPane);
                            e.consume(); // 阻止事件传播
                        }
                    }
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                // 更新鼠标样式
                updateMouseCursor(e, state.selectedFilesList);
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                // 更新鼠标样式
                updateMouseCursor(e, state.selectedFilesList);
            }
        });
        
        // 添加鼠标移动监听器
        state.selectedFilesList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateMouseCursor(e, state.selectedFilesList);
            }
        });
        
        JBScrollPane fileListScrollPane = new JBScrollPane(state.selectedFilesList);
        fileListScrollPane.setBorder(null);
        fileListScrollPane.setBackground(subtleFill(0));
        fileListScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        fileListScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        fileListScrollPane.setVisible(false); // 初始隐藏
        
        // 保存组件引用到state中
        state.fileCountLabel = fileCountLabel;
        state.fileListScrollPane = fileListScrollPane;
        
        // 按钮事件处理
        selectFileButton.addActionListener(e -> {
            handleSelectFiles(project);
            // handleSelectFiles中的addSelectedFiles已经处理了显示更新，不需要再次调用
        });
        clearButton.addActionListener(e -> {
            handleClearFiles(project);
            updateFileDisplay(state, state.fileCountLabel, state.fileListScrollPane);
        });
        
        // 组装面板
        contextPanel.add(topActionPanel, BorderLayout.NORTH);
        contextPanel.add(fileListScrollPane, BorderLayout.CENTER);
        
        state.contextPanel = contextPanel;
        state.selectFileButton = selectFileButton;
        
        return contextPanel;
    }
    
    /**
     * 处理文件选择 - 显示搜索下拉框
     */
    private void handleSelectFiles(Project project) {
        UIState state = uiStates.get(project);
        if (state == null) return;
        
        // 初始化项目文件列表（如果还没有初始化）
        if (state.projectFiles.isEmpty()) {
            loadProjectFiles(project, state);
        }
        
        // 创建搜索面板
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBackground(ideBackgroundColor);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        searchPanel.setPreferredSize(new Dimension(400, 300));
        
        // 搜索输入框
        JBTextField searchField = new JBTextField();
        searchField.getEmptyText().setText("输入文件名进行搜索...");
        searchField.setPreferredSize(new Dimension(0, 28));
        
        // 搜索结果列表
        JList<VirtualFile> resultsList = new JList<>(state.searchResultsModel);
        resultsList.setBackground(ideBackgroundColor);
        resultsList.setForeground(ideFontColor);
        resultsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // 设置文件列表渲染器
        resultsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof VirtualFile) {
                    VirtualFile file = (VirtualFile) value;
                    String relativePath = getRelativePathFromProject(project, file);
                    setText(relativePath);
                    setToolTipText(file.getPath());
                }
                setBackground(isSelected ? list.getSelectionBackground() : ideBackgroundColor);
                setForeground(isSelected ? list.getSelectionForeground() : ideFontColor);
                return this;
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(resultsList);
        scrollPane.setPreferredSize(new Dimension(0, 200));
        
        // 操作按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(ideBackgroundColor);
        
        JButton addButton = createActionButton("添加选中", ButtonStyle.PRIMARY);
        JButton cancelButton = createActionButton("取消", ButtonStyle.SECONDARY);
        
        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);
        
        // 组装面板
        searchPanel.add(searchField, BorderLayout.NORTH);
        searchPanel.add(scrollPane, BorderLayout.CENTER);
        searchPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 初始显示所有文件
        updateSearchResults(state, "");
        
        // 创建弹出窗口
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(searchPanel, searchField)
                .setTitle("选择项目文件")
                .setResizable(true)
                .setMovable(true)
                .setRequestFocus(true)
                .createPopup();
        
        // 搜索功能
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String searchText = searchField.getText().trim();
                updateSearchResults(state, searchText);
            }
        });
        
        // 双击添加文件
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    addSelectedFiles(state, resultsList, state.fileCountLabel, state.fileListScrollPane);
                    popup.closeOk(null);
                }
            }
        });
        
        // 按钮事件
        addButton.addActionListener(e -> {
            addSelectedFiles(state, resultsList, state.fileCountLabel, state.fileListScrollPane);
            popup.closeOk(null);
        });
        
        cancelButton.addActionListener(e -> popup.cancel());
        
        // 显示弹出窗口，相对于选择按钮
        popup.showUnderneathOf(state.selectFileButton);
    }
    
    /**
     * 加载项目文件列表
     */
    private void loadProjectFiles(Project project, UIState state) {
        ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);
        VirtualFile projectDir = project.getBaseDir();
        
        if (projectDir != null) {
            fileIndex.iterateContent(file -> {
                // 只包括文本文件，排除目录和二进制文件
                if (!file.isDirectory() && isTextFile(file)) {
                    state.projectFiles.add(file);
                }
                return true;
            });
        }
        
        // 按文件名排序
        state.projectFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
    }
    
    /**
     * 判断是否为文本文件
     */
    private boolean isTextFile(VirtualFile file) {
        String name = file.getName().toLowerCase();
        String[] textExtensions = {
            ".java", ".kt", ".scala", ".groovy",  // JVM语言
            ".xml", ".html", ".xhtml", ".jsp",    // 标记语言
            ".js", ".ts", ".json", ".css",       // Web技术
            ".properties", ".yml", ".yaml",      // 配置文件
            ".md", ".txt", ".rst",               // 文档
            ".sql", ".gradle", ".pom",           // 其他
            ".py", ".rb", ".go", ".rs",          // 其他编程语言
            ".sh", ".bat", ".cmd"                // 脚本
        };
        
        for (String ext : textExtensions) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 更新搜索结果
     */
    private void updateSearchResults(UIState state, String searchText) {
        state.searchResultsModel.clear();
        
        for (VirtualFile file : state.projectFiles) {
            if (searchText.isEmpty() || 
                file.getName().toLowerCase().contains(searchText.toLowerCase()) ||
                file.getPath().toLowerCase().contains(searchText.toLowerCase())) {
                state.searchResultsModel.addElement(file);
            }
        }
    }
    
    /**
     * 添加选中的文件
     */
    private void addSelectedFiles(UIState state, JList<VirtualFile> resultsList, JLabel fileCountLabel, JBScrollPane fileListScrollPane) {
        java.util.List<VirtualFile> selectedFiles = resultsList.getSelectedValuesList();
        
        for (VirtualFile file : selectedFiles) {
            String filePath = file.getPath();
            // 避免重复添加
            if (!state.selectedFilesPaths.contains(filePath)) {
                state.selectedFilesPaths.add(filePath);
                state.selectedFilesModel.addElement(filePath);
                
                // 预加载文件内容
                try {
                    String content = new String(file.contentsToByteArray(), file.getCharset());
                    state.fileContentsCache.put(filePath, content);
                } catch (Exception e) {
                    // 如果读取失败，记录错误但不影响使用
                    state.fileContentsCache.put(filePath, "文件读取失败: " + e.getMessage());
                }
            }
        }
        
        // 立即更新文件显示状态
        SwingUtilities.invokeLater(() -> {
            updateFileDisplay(state, fileCountLabel, fileListScrollPane);
        });
    }
    
    /**
     * 获取相对于项目的路径
     */
    private String getRelativePathFromProject(Project project, VirtualFile file) {
        VirtualFile projectDir = project.getBaseDir();
        if (projectDir != null) {
            String projectPath = projectDir.getPath();
            String filePath = file.getPath();
            if (filePath.startsWith(projectPath)) {
                return filePath.substring(projectPath.length() + 1);
            }
        }
        return file.getName();
    }
    
    /**
     * 清空选中的文件
     */
    private void handleClearFiles(Project project) {
        UIState state = uiStates.get(project);
        if (state == null) return;
        
        state.selectedFilesPaths.clear();
        state.selectedFilesModel.clear();
        state.fileContentsCache.clear();
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
        if (question.isEmpty() || question.equals(QUESTION_PLACEHOLDER)) {
            String errorMessage = "<div style='color: #ff6b6b; padding: 10px; background-color: rgba(255, 107, 107, 0.1); border-radius: 4px;'>请输入问题内容</div>";
            updateResult(errorMessage, project);
            return;
        }

        // 立即清空输入框并恢复placeholder
        ApplicationManager.getApplication().invokeLater(() -> {
            state.askButton.setVisible(false);
            state.cancelButton.setVisible(true);
            // 清空输入框并恢复placeholder状态
            state.questionTextArea.setText(QUESTION_PLACEHOLDER);
            applyQuestionTextAreaColors(state.questionTextArea, false);
        });

        // 不清空聊天历史，保持累积对话
        state.messageBuilder.setLength(0);
        state.currentAnswer.setLength(0);

        String qaId = String.valueOf(System.currentTimeMillis());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        String formattedQuestion = String.format(
                "<div class='chat-item message-row question-item' id='qa-%s'>" +
                        "<div class='message-bubble'>" +
                        "<div class='message-meta'>" +
                        "<span class='message-role'><span class='role-dot'></span>你</span>" +
                        "<span class='message-actions'><span>%s</span><button class='delete-btn' onclick='handleDeleteQA(\\\"qa-%s\\\")'>×</button></span>" +
                        "</div>" +
                        "<div class='message-content'>%s</div>" +
                        "</div>" +
                        "</div>",
                qaId, timestamp, qaId, question
        );

        state.chatHistory.append(formattedQuestion);
        // 不在这里添加分割线，等答案完成后统一添加
        
        // 立即显示问题到答案区域（显示完整的历史记录，包括刚添加的问题）
        updateResult(state.chatHistory.toString(), project);

        CodeService codeService = new CodeService();
        
        // 构建包含上下文文件的提示词
        StringBuilder promptBuilder = new StringBuilder();
        appendConversationContext(promptBuilder, state);
        
        // 添加上下文文件内容
        if (!state.selectedFilesPaths.isEmpty()) {
            promptBuilder.append("以下是相关的上下文文件内容，请结合这些文件内容来回答问题：\n\n");
            
            for (String filePath : state.selectedFilesPaths) {
                String content = state.fileContentsCache.get(filePath);
                if (content != null) {
                    String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
                    promptBuilder.append("### 文件: ").append(fileName).append("\n");
                    promptBuilder.append("```\n").append(content).append("\n```\n\n");
                }
            }
            
            promptBuilder.append("---\n\n");
        }
        
        // 添加用户问题
        promptBuilder.append("问题：").append(question).append("\n\n");
        promptBuilder.append("请根据提出的问题作出回答，用中文回答；若需编程，请给出示例，以Java作为默认编程语言输出");
        if (!state.selectedFilesPaths.isEmpty()) {
            promptBuilder.append("；如果上下文文件与问题相关，请结合文件内容进行分析和回答");
        }
        promptBuilder.append("。");
        
        String prompt = promptBuilder.toString();

        try {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "处理中", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    state.messageBuilder.setLength(0);
                    state.currentAnswer.setLength(0);
                    // 保持完整的历史记录
                    state.currentAnswer.append(state.chatHistory.toString());

                    AtomicReference<String> displayContent = new AtomicReference<>();
                    try {
                        if (codeService.generateByStream()) {
                            codeService.generateCommitMessageStream(
                                    prompt,
                                    token -> {
                                        state.messageBuilder.append(token);
                                        String formattedAnswer = String.format(
                                                "<div class='chat-item message-row answer-item' id='answer-%s'>" +
                                                        "<div class='message-bubble'>" +
                                                        "<div class='message-meta'>" +
                                                        "<span class='message-role'><span class='role-dot'></span>AI</span>" +
                                                        "<span>%s</span>" +
                                                        "</div>" +
                                                        "<div class='message-content'>%s</div>" +
                                                        "</div>" +
                                                        "</div>",
                                                qaId, timestamp, state.messageBuilder.toString()
                                        );
                                        displayContent.set(state.currentAnswer + formattedAnswer);
                                        updateResult(displayContent.get(), project);
                                    },
                                    error -> ApplicationManager.getApplication().invokeLater(() ->
                                            IdeaDialogUtil.showError(project, "处理失败: " + error.getMessage(), "Error")),
                                    () -> {
                                        // 将完整的问答对添加到历史记录
                                        String finalAnswer = String.format(
                                                "<div class='chat-item message-row answer-item' id='answer-%s'>" +
                                                        "<div class='message-bubble'>" +
                                                        "<div class='message-meta'>" +
                                                        "<span class='message-role'><span class='role-dot'></span>AI</span>" +
                                                        "<span>%s</span>" +
                                                        "</div>" +
                                                        "<div class='message-content'>%s</div>" +
                                                        "</div>" +
                                                        "</div>",
                                                qaId, timestamp, state.messageBuilder.toString()
                                        );
                                        // 只在答案后面添加一条分割线，为下一轮对话做准备
                                        String answerText = state.messageBuilder.toString();
                                        addConversationTurn(state, question, answerText);
                                        state.chatHistory.append(finalAnswer);
                                        state.chatHistory.append("<hr class='chat-separator'>");
                                        
                                        saveHistory(project, question, answerText);
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            resetButton(project);
                                            // 不需要再次设置placeholder，因为已经在点击时设置了
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

    private void appendConversationContext(StringBuilder promptBuilder, UIState state) {
        if (state.conversationTurns.isEmpty()) {
            return;
        }

        promptBuilder.append("以下是当前会话的历史对话，请结合上下文理解用户最新问题；如果历史与最新问题无关，可以忽略。\n\n");
        int start = Math.max(0, state.conversationTurns.size() - MAX_CONVERSATION_CONTEXT_TURNS);
        for (int i = start; i < state.conversationTurns.size(); i++) {
            ConversationTurn turn = state.conversationTurns.get(i);
            promptBuilder.append("用户：").append(turn.question).append("\n");
            promptBuilder.append("助手：").append(turn.answer).append("\n\n");
        }
        promptBuilder.append("---\n\n");
    }

    private void addConversationTurn(UIState state, String question, String answer) {
        state.conversationTurns.add(new ConversationTurn(question, answer));
        if (state.conversationTurns.size() > MAX_CONVERSATION_CONTEXT_TURNS) {
            state.conversationTurns.remove(0);
        }
    }

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
                        state.outputPanel.setBackground(ideBackgroundColor);
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

    /**
     * 更新鼠标光标样式
     */
    private void updateMouseCursor(MouseEvent e, JList<String> fileList) {
        int index = fileList.locationToIndex(e.getPoint());
        if (index >= 0 && index < fileList.getModel().getSize()) {
            Rectangle cellBounds = fileList.getCellBounds(index, index);
            if (cellBounds != null) {
                int relativeX = e.getX() - cellBounds.x;
                int cellWidth = cellBounds.width;
                // 在删除按钮区域显示手型光标
                if (relativeX > cellWidth - 22 && relativeX < cellWidth) {
                    fileList.setCursor(new Cursor(Cursor.HAND_CURSOR));
                } else {
                    fileList.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        } else {
            fileList.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }
    
    /**
     * 自定义文件列表渲染器 - 带删除按钮
     */
    private class FileListCellRenderer extends JPanel implements ListCellRenderer<String> {
        private final Project project;
        private JLabel fileLabel;
        private JLabel deleteLabel;
        private String filePath;
        private boolean isSelected;
        
        public FileListCellRenderer(Project project) {
            this.project = project;
            setLayout(new BorderLayout());
            setOpaque(true);
            
            // 文件名标签
            fileLabel = new JLabel();
            fileLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            fileLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            add(fileLabel, BorderLayout.CENTER);
            
            // 删除按钮标签 - 美化样式
            deleteLabel = new JLabel("×");
            deleteLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            deleteLabel.setForeground(new Color(180, 180, 180));
            deleteLabel.setHorizontalAlignment(SwingConstants.CENTER);
            deleteLabel.setVerticalAlignment(SwingConstants.CENTER);
            deleteLabel.setPreferredSize(new Dimension(18, 18));
            deleteLabel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 3));
            deleteLabel.setOpaque(true);
            deleteLabel.setBackground(new Color(245, 245, 245));
            deleteLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            add(deleteLabel, BorderLayout.EAST);
        }
        
        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            this.filePath = value;
            this.isSelected = isSelected;
            
            if (value != null) {
                String fileName = value.substring(value.lastIndexOf('/') + 1);
                fileLabel.setText("● " + fileName);
                setToolTipText(value);
            }
            
            Color backgroundColor = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color foregroundColor = isSelected ? list.getSelectionForeground() : list.getForeground();
            
            setBackground(backgroundColor);
            fileLabel.setForeground(foregroundColor);
            fileLabel.setBackground(backgroundColor);
            
            // 跟随列表选中态颜色，避免在浅色/深色主题下出现黑块或不可读文本。
            if (isSelected) {
                deleteLabel.setBackground(backgroundColor);
                deleteLabel.setForeground(foregroundColor);
            } else {
                deleteLabel.setBackground(backgroundColor);
                deleteLabel.setForeground(new Color(180, 180, 180));
            }
            
            return this;
        }
    }

}
