package com.yohannzhang.aigit.config;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.util.ui.JBUI;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.service.AIService;
import com.yohannzhang.aigit.service.CommitMessageService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.Map;

public class ModuleConfigDialog extends DialogWrapper {
    private JTextField urlField;
    private JBPasswordField apiKeyField;
    private final String client;
    private final String module;
    // 文字提示
    private JLabel helpLabel;
    private JButton resetButton; // 新增重置按钮
    private JButton checkConfigButton; // 校验当前配置是否正确
    private ApiKeySettings.ModuleConfig originalConfig; // 保存原始配置
    private boolean isPasswordVisible = false;


    public ModuleConfigDialog(Component parent, String client, String module) {
        super(parent, true);
        this.client = client;
        this.module = module;
        setTitle(client + " : " + module);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // URL field
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("URL:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        urlField = new JTextField(30);
        panel.add(urlField, gbc);

        // API Key field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("API Key:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        apiKeyField = new JBPasswordField();
        panel.add(apiKeyField, gbc);

        // Help text
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        helpLabel = new JLabel("Please enter your API configuration");
        helpLabel.setForeground(JBColor.GRAY);
        panel.add(helpLabel, gbc);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        resetButton = new JButton("Reset");
        checkConfigButton = new JButton("Check Config");
        buttonPanel.add(resetButton);
        buttonPanel.add(checkConfigButton);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private void updateHelpText() {
        helpLabel.setText(Constants.getHelpText(client));

        String url = Constants.CLIENT_HELP_URLS.get(client);
        if (url != null) {
            helpLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            helpLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        Desktop.getDesktop().browse(new URI(url));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        // 可以考虑添加错误提示
                        // Messages.showErrorDialog("无法打开链接: " + ex.getMessage(), "错误");
                    }
                }
            });
        }
    }

    @Override
    protected Action @NotNull [] createActions() {
        resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> resetFields());

        checkConfigButton = new JButton("Check Config");
        checkConfigButton.addActionListener(e -> checkConfig());

        return new Action[]{
                getOKAction(),
                getCancelAction(),
                new DialogWrapperAction("Reset") {
                    @Override
                    protected void doAction(ActionEvent e) {
                        resetFields();
                    }
                },
                new DialogWrapperAction("Check Config") {
                    @Override
                    protected void doAction(ActionEvent e) {
                        checkConfig();
                    }
                }
        };
    }

    private void checkConfig() {
        ProgressManager.getInstance().run(new Task.Modal(null, "Validating Configuration", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(true);
                    indicator.setText("Validating configuration...");

                    AIService aiService = CommitMessageService.getAIService(client);
                    Map<String, String> checkConfig = Map.of(
                            "url", urlField.getText(),
                            "module", module,
                            "apiKey", new String(apiKeyField.getPassword()));

                    boolean isValid = aiService.validateConfig(checkConfig);

                    // Use invokeLater to ensure dialogs are shown in EDT thread
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (isValid) {
                            Messages.showInfoMessage("Configuration validation successful", "Success");
                        } else {
                            Messages.showErrorDialog(
                                    "Configuration validation failed. You can click the reset button to restore default values.",
                                    "Error");
                        }
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(
                                "Validation error occurred: " + e.getMessage(),
                                "Error");
                    });
                }
            }
        });
    }

    @Override
    protected void init() {
        super.init();
        ApiKeySettings settings = ApiKeySettings.getInstance();
        String configKey = client;
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs()
                .computeIfAbsent(configKey, k -> {
                    ApiKeySettings.ModuleConfig defaultConfig = Constants.moduleConfigs.get(configKey);
                    ApiKeySettings.ModuleConfig config = new ApiKeySettings.ModuleConfig();
                    config.setUrl(defaultConfig.getUrl());
                    config.setApiKey(defaultConfig.getApiKey());
                    return config;
                });
        urlField.setText(moduleConfig.getUrl());
        apiKeyField.setText(moduleConfig.getApiKey());
    }

    @Override
    protected void doOKAction() {
        ApiKeySettings settings = ApiKeySettings.getInstance();
        String configKey = client;
        ApiKeySettings.ModuleConfig moduleConfig = settings.getModuleConfigs()
                .computeIfAbsent(configKey, k -> new ApiKeySettings.ModuleConfig());

        String url = urlField.getText().trim();
        String apiKey = new String(apiKeyField.getPassword());
        if (StringUtils.isEmpty(url)) {
            Messages.showErrorDialog("URL cannot be empty", "Error");
            return;
        }

        if (client.equals(Constants.Gemini) || client.equals(Constants.CloudflareWorkersAI)) {
            if (StringUtils.isEmpty(apiKey)) {
                Messages.showErrorDialog("API Key cannot be empty", "Error");
                return;
            }
        }

        moduleConfig.setApiKey(apiKey);
        moduleConfig.setUrl(url);

        super.doOKAction();
    }

    private void resetFields() {
        // 重置为默认配置
        ApiKeySettings.ModuleConfig defaultConfig = Constants.moduleConfigs.get(client);
        if (defaultConfig != null) {
            urlField.setText(defaultConfig.getUrl());
            apiKeyField.setText(defaultConfig.getApiKey());
        }
    }

    private void togglePasswordVisibility(JBPasswordField passwordField, JButton toggleButton) {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            // 显示密码
            String password = new String(passwordField.getPassword());
            passwordField.setEchoChar((char) 0); // 设置为不隐藏字符
            toggleButton.setIcon(AllIcons.Actions.Show); // 切换到"隐藏"图标
        } else {
            // 隐藏密码
            passwordField.setEchoChar('•'); // 恢复为密码字符
            toggleButton.setIcon(AllIcons.Actions.Show); // 切换到"显示"图标
        }
        passwordField.revalidate();
        passwordField.repaint();
    }
}