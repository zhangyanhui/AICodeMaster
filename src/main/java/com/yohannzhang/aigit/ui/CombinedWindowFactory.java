package com.yohannzhang.aigit.ui;

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
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import com.yohannzhang.aigit.util.OpenAIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
        
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultConstraints();

        panel.add(createOutputPanel(project), gbc);
        gbc.gridy = 1;
        gbc.weighty = 0.1;
        panel.add(createInputPanel(project), gbc);

        Content content = toolWindow.getContentManager().getFactory().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        // 确保在创建完所有组件后刷新UI
        ApplicationManager.getApplication().invokeLater(this::refreshUIOnThemeChange);
        AIGuiComponent.getInstance(project).setWindowFactory(this);

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
        outputPanel = new JPanel(new BorderLayout(10, 10));
//        outputPanel.setBorder(BorderFactory.createTitledBorder("输出结果"));
        outputPanel.setBackground(ideBackgroundColor);
        // 如果需要调整输入/输出面板的边框（可选）
        outputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(150, 150, 150), 0), // 添加1px边框
                ""
        ));


        markdownViewer = new JBCefBrowser();
        markdownViewer.getComponent().setBorder(BorderFactory.createEmptyBorder());
        String EMPTY_HTML = readResourceFile("empty.html");

        String script = "document.documentElement.style.setProperty('--font-size', '" + fontSize + "px');" +
                "document.documentElement.style.setProperty('--workspace-color', '" + toHex(ideBackgroundColor) + "');" +
                "document.documentElement.style.setProperty('--idefont-color', '" + toHex(ideFontColor) + "');";

        System.out.println("2IDE Font Color: " + toHex(ideFontColor));
        System.out.println("2IDE Background Color: " + toHex(ideBackgroundColor));
        // 初始加载空 HTML 页面
        ApplicationManager.getApplication().invokeLater(() -> {
            markdownViewer.loadHTML(EMPTY_HTML);
            markdownViewer.getCefBrowser().executeJavaScript(script, "about:blank", 0);

        });

        outputPanel.add(markdownViewer.getComponent(), BorderLayout.CENTER);

        return outputPanel;
    }

    // 将 Color 对象转换为十六进制颜色字符串
    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private JPanel createInputPanel(Project project) {
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBackground(ideBackgroundColor);

        String[] clientArr = ApiKeySettings.getInstance().getAvailableModels();
        // 创建模型选择下拉框
        modelComboBox = new JComboBox<>(clientArr);
        //设置默认值
        modelComboBox.setSelectedItem(ApiKeySettings.getInstance().getSelectedModule());
        modelComboBox.setPreferredSize(new Dimension(120, 30));
        modelComboBox.setBackground(ideBackgroundColor);
        modelComboBox.setForeground(ideFontColor);
        modelComboBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        
        // 使用 JPanel 包裹下拉框，便于布局控制
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.setBackground(ideBackgroundColor);
        leftPanel.add(modelComboBox);

        // 创建按钮面板并添加组件
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(ideBackgroundColor);
        askButton = createStyledButton("提交");
        cancelButton = createStyledButton("取消");
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

        String placeholderText = "请输入问题";
        questionTextArea.setText(placeholderText);
        questionTextArea.setForeground(ideBackgroundColor.brighter().brighter());

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
                    questionTextArea.setForeground(ideBackgroundColor.brighter().brighter());
                }
            }
        });

        // 调整文本输入框边框
        questionTextArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100), 0),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
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
//        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20)); // 增加内边距
        button.setOpaque(true);
        button.setForeground(ideFontColor);
        button.setFocusPainted(false);
//        button.setBorderPainted(true); // 启用边框绘制
        button.setCursor(new Cursor(Cursor.HAND_CURSOR)); // 鼠标悬停时显示手型
        // 修改按钮边框粗细
        button.setBorder(BorderFactory.createCompoundBorder(
                // 将线框厚度从2调整为1
                BorderFactory.createLineBorder(new Color(22, 93, 255), 0),
                // 可选：调整内边距（原为10,20,10,20）
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));


        // 设置按钮的 preferredSize
        button.setPreferredSize(new Dimension(80, 30));

        // 圆角效果
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(22, 93, 255), 1),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));

        // 设置默认背景颜色
        button.setBackground(BUTTON_COLOR);

        // 鼠标悬停效果
//        button.addMouseListener(new java.awt.event.MouseAdapter() {
//            @Override
//            public void mouseEntered(java.awt.event.MouseEvent e) {
//                button.setBackground(new Color(13, 46, 136));
//            }
//
//            @Override
//            public void mouseExited(java.awt.event.MouseEvent e) {
//                // 设置背景颜色为默认颜色
//                button.setBackground(BUTTON_COLOR);
//            }
//        });

        return button;
    }


    private void handleAskButtonClick(Project project) {
        String question = questionTextArea.getText().trim();
        if (question.isEmpty()) {
            updateResult("请输入问题！");
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            askButton.setVisible(false);
            cancelButton.setVisible(true);
        });

        CodeService codeService = new CodeService();
        String formattedCode = CODE_UTIL.formatCode(question);
        String prompt = String.format("根据提出的问题作出回答，用中文回答；若需编程，请给出示例，以Java作为默认编程语言输出；问题如下：%s", formattedCode);

        try {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "处理中", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    messageBuilder.setLength(0); // 重置内容构建器
                    try {
                        if (codeService.generateByStream()) {
                            codeService.generateCommitMessageStream(
                                    prompt,
                                    token -> {
                                        messageBuilder.append(token);
                                        String fullMarkdown = messageBuilder.toString();
                                        updateResult(fullMarkdown);
                                    },
                                    this::handleErrorResponse,
                                    () -> ApplicationManager.getApplication().invokeLater(() -> {
                                        //把以下两行抽成一个方法
                                        resetButton();
                                    })
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


}