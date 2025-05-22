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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CombinedWindowFactory implements ToolWindowFactory {
    private static final CodeUtil CODE_UTIL = new CodeUtil();
    private static final Font BUTTON_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Color BUTTON_COLOR = new Color(32, 86, 105);
    private static final Color BUTTON_HOVER_COLOR = new Color(143, 0, 200);

    private JTextArea questionTextArea;
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
        String[] clientArr = ApiKeySettings.getInstance().getAvailableModels();
        // 创建模型选择下拉框
        modelComboBox = new JComboBox<>(clientArr);
        //设置默认值
        modelComboBox.setSelectedItem(ApiKeySettings.getInstance().getSelectedModule());
        modelComboBox.setPreferredSize(new Dimension(120, 30));
        modelComboBox.setBackground(ideBackgroundColor);
        modelComboBox.setForeground(Color.WHITE);
        modelComboBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        //切换模型时处理逻辑，同步设置ApiKeySettings
        // 添加模型切换事件监听器
        modelComboBox.addActionListener(e -> {
            // 更新 ApiKeyConfigurableUI 中的模型选择
            ApiKeyConfigurableUI ui = new ApiKeyConfigurableUI();
            String selectedClient = (String) modelComboBox.getSelectedItem();
            ApiKeySettings.getInstance().setSelectedClient(selectedClient);

            ui.updateModuleComboBox(selectedClient);
            String[] modleArr = Constants.CLIENT_MODULES.get(selectedClient);
            if(modleArr != null){
                ui.updateModuleComboBox(modleArr[0]);
                ApiKeySettings.getInstance().setSelectedModule(modleArr[0]);
            }

        });

        // 使用 JPanel 包裹下拉框，便于布局控制
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.setBackground(ideBackgroundColor);
        leftPanel.add(modelComboBox);

        questionTextArea = new JTextArea(3, 50);
        questionTextArea.setLineWrap(true);
        questionTextArea.setWrapStyleWord(true);
        questionTextArea.setBackground(ideBackgroundColor); // 输入框背景与 IDE 一致
        questionTextArea.setForeground(Color.WHITE); // 文本颜色可根据需要调整
        questionTextArea.requestFocusInWindow();
        //显示为可编辑
        questionTextArea.setEditable(true);

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

        // 添加所有组件到输入面板
        inputPanel.add(topPanel, BorderLayout.SOUTH); // 模型选择和按钮在顶部
        inputPanel.add(new JBScrollPane(questionTextArea), BorderLayout.CENTER); // 输入区域在中间

        AIGuiComponent.getInstance(project).setWindowFactory(this);

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
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20)); // 增加内边距
        button.setOpaque(true);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true); // 启用边框绘制
        button.setCursor(new Cursor(Cursor.HAND_CURSOR)); // 鼠标悬停时显示手型

        // 设置按钮的 preferredSize
        button.setPreferredSize(new Dimension(80, 30));

        // 圆角效果
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(22, 93, 255), 2),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));

        // 设置默认背景颜色
        button.setBackground(BUTTON_COLOR);

        // 鼠标悬停效果
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(new Color(13, 46, 136));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                // 设置背景颜色为默认颜色
                button.setBackground(BUTTON_COLOR);
            }
        });

        return button;
    }




    private void handleAskButtonClick(Project project) {
        //弹窗
//        JOptionPane.showMessageDialog(null, "请稍等...", "提示", JOptionPane.INFORMATION_MESSAGE);
        String question = questionTextArea.getText().trim();
        if (question.isEmpty()) {
            updateResult("请输入问题！");
            return;
        }

        // 显示加载动画，隐藏按钮（使用 invokeLater 确保在 EDT 执行）
        ApplicationManager.getApplication().invokeLater(() -> {
//            loadingLabel.setVisible(true);
            // 如果有 askButton 成员变量，可以取消显示
            askButton.setVisible(false);
            cancelButton.setVisible(true);
        });

        // 先展示问题
//        String formattedQuestion = "> 问题：" + question + "\n\n -----";
//        updateResult(formattedQuestion); // 显示问题部分
//        isInitialResponse = false;

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
                                        askButton.setVisible(true);
                                        cancelButton.setVisible(false);
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