package com.yohannzhang.aigit.ui;

import com.intellij.openapi.actionSystem.CustomShortcutSet;
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
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.jcef.JBCefBrowser;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class CombinedWindowFactory implements ToolWindowFactory, EditorColorsListener {
    private MessageBusConnection messageBusConnection; // 用于订阅事件总线
    private static final CodeUtil CODE_UTIL = new CodeUtil();
    private static final Font BUTTON_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Color BUTTON_COLOR = new Color(32, 86, 105);
    private static final Color BUTTON_HOVER_COLOR = new Color(143, 0, 200);

    private JTextArea questionTextArea;
    private JPanel outputPanel;
    private final StringBuilder messageBuilder = new StringBuilder();
    private JBCefBrowser markdownViewer; // 使用 JBCefBrowser 替换 JEditorPane
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
        
        // 创建消息连接并监听主题变化
        messageBusConnection = project.getMessageBus().connect();
        messageBusConnection.subscribe(EditorColorsManager.TOPIC, this);
        
        // Add history icon to tool window
        //调整icon
        AnAction historyAction = new AnAction("Show Chat History", "Show chat history", AllIcons.Vcs.History) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                toggleHistoryView();
            }
        };

        toolWindow.setTitleActions(Collections.singletonList(historyAction));


//        AnAction homeAction = new AnAction("Home", "Return to home page", AllIcons.Actions.Copy) {
//            @Override
//            public void actionPerformed(@NotNull AnActionEvent e) {
//                showWelcomePage();
//            }
//        };
        toolWindow.setTitleActions(Arrays.asList( historyAction));


        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultConstraints();

        panel.add(createOutputPanel(project), gbc);
        gbc.gridy = 1;
        gbc.weighty = 0.1;
        panel.add(createInputPanel(project), gbc);

        Content content = toolWindow.getContentManager().getFactory().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        // 在 createToolWindowContent 中为 action 添加快捷键
        historyAction.registerCustomShortcutSet(
                new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK)),
                panel);

        // 确保在创建完所有组件后刷新UI
        ApplicationManager.getApplication().invokeLater(this::refreshUIOnThemeChange);
        AIGuiComponent.getInstance(project).setWindowFactory(this);
        // 在createToolWindowContent方法中添加:
//        loadHistory();
    }
    public void showWelcomePage() {
        String EMPTY_HTML = readResourceFile("welcome.html");
        markdownViewer.loadHTML(EMPTY_HTML);
        isHistoryView = false; // 确保不在历史视图

        // 刷新UI确保显示正确
        outputPanel.removeAll();
        outputPanel.add(markdownViewer.getComponent(), BorderLayout.CENTER);
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
            questionTextArea.setBackground(ideBackgroundColor);
            questionTextArea.setForeground(ideFontColor);
        }
        if (outputPanel != null) {
            outputPanel.setBackground(ideBackgroundColor);
            outputPanel.setForeground(ideFontColor);
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
                "document.documentElement.style.setProperty('--idefont-color', '" + toHex(ideFontColor) + "');";

        if (markdownViewer != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                markdownViewer.getCefBrowser().executeJavaScript(script, "about:blank", 0);
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
        int rgb = ideBackgroundColor.getRGB();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

        // 动态调整前景色
        if (brightness < 0.5) {
            ideFontColor = Color.WHITE;
        } else {
            ideFontColor = Color.BLACK;
        }
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
                "document.getElementById('content').innerHTML = `%s`; " +
                "document.querySelectorAll('pre code').forEach((block) => { hljs.highlightElement(block); }); " +
                "addCopyButtons(); " +
                "window.scrollTo(0, document.body.scrollHeight);",
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
        outputPanel = new JPanel(new BorderLayout(10, 10));
        outputPanel.setBackground(ideBackgroundColor);
        outputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                "AI 助手"
        ));

        markdownViewer = new JBCefBrowser();
        markdownViewer.getComponent().setBorder(BorderFactory.createEmptyBorder());
        String EMPTY_HTML = "<!DOCTYPE html>\n" +
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
                "    <div id='content'>\n" +
                "        <div class='welcome-container'>\n" +
                "            <div class='welcome-header'>\n" +
                "                <div class='welcome-title'>AI 代码助手</div>\n" +
                "                <div class='welcome-subtitle'>您的智能编程伙伴</div>\n" +
                "            </div>\n" +
                "            <div class='feature-grid'>\n" +
                "                <div class='feature-card'>\n" +
                "                    <div class='feature-icon'>💡</div>\n" +
                "                    <div class='feature-title'>智能代码优化</div>\n" +
                "                    <div class='feature-desc'>自动分析代码质量，提供优化建议，提升代码性能和可维护性</div>\n" +
                "                </div>\n" +
                "                <div class='feature-card'>\n" +
                "                    <div class='feature-icon'>🔍</div>\n" +
                "                    <div class='feature-title'>问题诊断</div>\n" +
                "                    <div class='feature-desc'>快速定位代码问题，提供详细的错误分析和解决方案</div>\n" +
                "                </div>\n" +
                "                <div class='feature-card'>\n" +
                "                    <div class='feature-icon'>🔄</div>\n" +
                "                    <div class='feature-title'>代码重构</div>\n" +
                "                    <div class='feature-desc'>提供专业的重构建议，帮助改进代码结构和设计模式</div>\n" +
                "                </div>\n" +
                "                <div class='feature-card'>\n" +
                "                    <div class='feature-icon'>💬</div>\n" +
                "                    <div class='feature-title'>智能问答</div>\n" +
                "                    <div class='feature-desc'>解答编程问题，提供代码示例，支持多语言开发</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div class='quick-start'>\n" +
                "                <div class='quick-start-title'>快速开始</div>\n" +
                "                <ul class='quick-start-list'>\n" +
                "                    <li class='quick-start-item'>在输入框中输入您的问题或需求</li>\n" +
                "                    <li class='quick-start-item'>选择合适的大语言模型</li>\n" +
                "                    <li class='quick-start-item'>点击提交按钮或按回车发送</li>\n" +
                "                    <li class='quick-start-item'>查看 AI 助手的回答和建议</li>\n" +
                "                </ul>\n" +
                "            </div>\n" +
                "            <div class='welcome-footer'>\n" +
                "                开始您的智能编程之旅吧\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
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
                "            // 删除问题和答案以及分隔线\n" +
                "            if (questionDiv) questionDiv.remove();\n" +
                "            if (nextHr) nextHr.remove();\n" +
                "            if (answerDiv) answerDiv.remove();\n" +
                "            if (nextHr2) nextHr2.remove();\n" +
                "            \n" +
                "            // 如果删除后没有内容了，显示欢迎页面\n" +
                "            if (document.querySelectorAll('.chat-item').length === 0) {\n" +
                "                document.getElementById('content').innerHTML = document.querySelector('.welcome-container').outerHTML;\n" +
                "            }\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";

        String script = "document.documentElement.style.setProperty('--font-size', '" + fontSize + "px');" +
                "document.documentElement.style.setProperty('--workspace-color', '" + toHex(ideBackgroundColor) + "');" +
                "document.documentElement.style.setProperty('--idefont-color', '" + toHex(ideFontColor) + "');";

        ApplicationManager.getApplication().invokeLater(() -> {
            markdownViewer.loadHTML(EMPTY_HTML);
            markdownViewer.getCefBrowser().executeJavaScript(script, "about:blank", 0);
        });

        outputPanel.add(markdownViewer.getComponent(), BorderLayout.CENTER);

        return outputPanel;
    }

    private void toggleHistoryView() {
        if (!isHistoryView) {
            // 显示历史面板
            if (historyPanel == null) {
                createHistoryPanel();
            }
            outputPanel.remove(markdownViewer.getComponent());
            outputPanel.add(historyPanel, BorderLayout.CENTER);
            updateHistoryList();
        } else {
            // 显示主内容
            outputPanel.remove(historyPanel);
            outputPanel.add(markdownViewer.getComponent(), BorderLayout.CENTER);
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
            markdownViewer.loadHTML(EMPTY_HTML);
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
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBackground(ideBackgroundColor);

        String[] clientArr = ApiKeySettings.getInstance().getAvailableModels();
        // 创建模型选择下拉框
        modelComboBox = new JComboBox<>(clientArr);
        //设置默认值
        modelComboBox.setSelectedItem(ApiKeySettings.getInstance().getSelectedModule());
        modelComboBox.setPreferredSize(new Dimension(120, 32));
        modelComboBox.setBackground(ideBackgroundColor);
        modelComboBox.setForeground(ideFontColor);
        modelComboBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        
        // 使用 JPanel 包裹下拉框，便于布局控制
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.setBackground(ideBackgroundColor);
        leftPanel.add(modelComboBox);

        // 创建按钮面板并添加组件
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBackground(ideBackgroundColor);
        askButton = createStyledButton("提交");
        cancelButton = createStyledButton("取消");
        cancelButton.setBackground(new Color(231, 76, 60));
        cancelButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(231, 76, 60), 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
        cancelButton.setVisible(false); // 初始隐藏

        // 为 askButton 添加点击事件处理
        askButton.addActionListener(e -> handleAskButtonClick(project));
        // 为 cancelButton 添加点击事件处理
        cancelButton.addActionListener(e -> handleCancel());

        buttonPanel.add(askButton);
        buttonPanel.add(cancelButton);

        // 创建包含下拉框和按钮的面板
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ideBackgroundColor);
        topPanel.add(leftPanel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        // 在createInputPanel方法中，找到questionTextArea初始化部分：
        questionTextArea = new JTextArea(3, 50);
        questionTextArea.setLineWrap(true);
        questionTextArea.setWrapStyleWord(true);
        questionTextArea.setBackground(ideBackgroundColor);
        questionTextArea.setForeground(ideFontColor);
        questionTextArea.requestFocusInWindow();

        String placeholderText = "输入问题，按回车发送";
        questionTextArea.setText(placeholderText);
        questionTextArea.setForeground(new Color(128, 128, 128)); // 使用灰色显示占位符

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
                    questionTextArea.setForeground(new Color(128, 128, 128));
                }
            }
        });

        // 调整文本输入框边框
        questionTextArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        questionTextArea.setEditable(true);

        // 添加所有组件到输入面板
        inputPanel.add(topPanel, BorderLayout.SOUTH);
        inputPanel.add(new JBScrollPane(questionTextArea), BorderLayout.CENTER);

        // 确保所有组件都使用正确的主题颜色
        refreshComponentBackground(inputPanel);

        return inputPanel;
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


    private void handleAskButtonClick(Project project) {
        String question = questionTextArea.getText().trim();
        if (question.isEmpty() || question.equals("输入问题，按回车发送")) {
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
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formattedQuestion = String.format(
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

        chatHistory.append(formattedQuestion);
        chatHistory.append("<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>");

        CodeService codeService = new CodeService();
        String formattedCode = CODE_UTIL.formatCode(question);
        String prompt = String.format("根据提出的问题作出回答，用中文回答；若需编程，请给出示例，以Java作为默认编程语言输出；问题如下：%s", formattedCode);

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
                                        "<div style='display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px;'>" +
                                        "<strong style='color: #2196F3;'>A:</strong>" +
                                        "<span style='color: #666; font-size: 0.9em;'>%s</span>" +
                                        "</div>" +
                                        "<div style='margin-left: 20px;'>%s</div>" +
                                        "</div>",
                                        timestamp, currentAnswer
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
                        IdeaDialogUtil.showError(project, "处理失败: " + error.getMessage(), "Error");
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
}