package com.yohannzhang.aigit.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.pojo.PromptInfo;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;

public class ApiKeyConfigurable implements Configurable {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyConfigurable.class);
    private ApiKeyConfigurableUI ui;
    private final ApiKeySettings settings = ApiKeySettings.getInstance();

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "AI Code Master";
    }

    //获取当前ui
    public ApiKeyConfigurableUI getUi() {
        return ui;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        ui = new ApiKeyConfigurableUI();
        loadSettings();
        loadUiState(settings.getSelectedClient(),settings.getSelectedModule());
        return ui.getMainPanel();
    }

    @Override
    public boolean isModified() {
        return !settings.getSelectedClient().equals(ui.getClientComboBox().getSelectedItem())
                || !settings.getSelectedModule().equals(ui.getModuleComboBox().getSelectedItem())
                || !settings.getCommitLanguage().equals(ui.getLanguageComboBox().getSelectedItem())
                || isCustomPromptsModified() || isCustomPromptModified() || isPromptTypeModified();
//        return true;
    }

    @Override
    public void apply() {
        if (ui == null) {
            return;  // 如果UI已经被销毁，直接返回
        }

        // 保存当前设置到临时变量
        String selectedClient = (String) ui.getClientComboBox().getSelectedItem();
        String selectedModule = (String) ui.getModuleComboBox().getSelectedItem();
        String commitLanguage = (String) ui.getLanguageComboBox().getSelectedItem();

        // 应用设置
        settings.setSelectedClient(selectedClient);
        settings.setSelectedModule(selectedModule);
        settings.setCommitLanguage(commitLanguage);

        // 保存prompt内容
        Object selectedPromptType = ui.getPromptTypeComboBox().getSelectedItem();
        if (Constants.CUSTOM_PROMPT.equals(selectedPromptType)) {
            saveCustomPromptsAndChoosedPrompt();
        }
        // 保存prompt类型
        settings.setPromptType((String) selectedPromptType);
        //更新输出面板模型的赋值
        loadUiState(selectedClient, selectedModule);



    }
    public void loadUiState(String selectedClient, String selectedModule){
        //如何获取project
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length > 0) {
            for (Project project : projects) {
                // 处理每个 project
                CombinedWindowFactory combinedWindowFactory = CombinedWindowFactory.getInstance(project);
                CombinedWindowFactory.UIState uiStateIState = combinedWindowFactory.uiStates.get(project);
                if(uiStateIState!=null){
                    uiStateIState.modelComboBox.setSelectedItem(selectedClient);
                    uiStateIState.modelSelectComboBox.setSelectedItem(selectedModule);
                }


            }
        }
    }

    @Override
    public void reset() {
        loadSettings();
    }

    @Override
    public void disposeUIResources() {
        ui = null;
    }

    private void loadSettings() {
        if (ui != null) {
            ui.getClientComboBox().setSelectedItem(settings.getSelectedClient());
            ui.getModuleComboBox().setSelectedItem(settings.getSelectedModule());
            ui.getLanguageComboBox().setSelectedItem(settings.getCommitLanguage());

            // 设置表格数据
            loadCustomPrompts();
            // 设置下拉框选中项
            loadChoosedPrompt();

            // 设置提示类型
            ui.getPromptTypeComboBox().setSelectedItem(settings.getPromptType());
        }
    }

    private void loadCustomPrompts() {
        DefaultTableModel model = (DefaultTableModel) ui.getCustomPromptsTable().getModel();
        model.setRowCount(0);
        for (PromptInfo prompt : settings.getCustomPrompts()) {
            if (prompt != null) {
                model.addRow(new String[]{prompt.getDescription(), prompt.getPrompt()});
            }
        }
    }

    private void loadChoosedPrompt() {
        if (settings.getCustomPrompt() != null) {
            DefaultTableModel model = (DefaultTableModel) ui.getCustomPromptsTable().getModel();
            int rowCount = model.getRowCount();
            for (int i = 0; i < rowCount; i++) {
                String description = (String) model.getValueAt(i, 0);
                String prompt = (String) model.getValueAt(i, 1);
                if (settings.getCustomPrompt().getDescription().equals(description)
                        && settings.getCustomPrompt().getPrompt().equals(prompt)) {
                    ui.getCustomPromptsTable().setRowSelectionInterval(i, i);
                }
            }
        }
    }

    private void saveCustomPromptsAndChoosedPrompt() {
        DefaultTableModel model = (DefaultTableModel) ui.getCustomPromptsTable().getModel();
        int selectedRow = ui.getSELECTED_ROW();
        int rowCount = model.getRowCount();
        List<PromptInfo> customPrompts = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            String description = (String) model.getValueAt(i, 0);
            String prompt = (String) model.getValueAt(i, 1);
            PromptInfo promptInfo = new PromptInfo(description, prompt);
            customPrompts.add(i, promptInfo);

            // 处理选中的行数据作为新的prompt
            if (selectedRow == i) {
                settings.setCustomPrompt(promptInfo);
            }
        }
        settings.setCustomPrompts(customPrompts);
    }

    private boolean isPromptTypeModified() {
        Object selectedPromptType = ui.getPromptTypeComboBox().getSelectedItem();
        return !settings.getPromptType().equals(selectedPromptType);
    }

    private boolean isCustomPromptsModified() {
        DefaultTableModel model = (DefaultTableModel) ui.getCustomPromptsTable().getModel();
        int rowCount = model.getRowCount();
        if (rowCount != settings.getCustomPrompts().size()) {
            return true;
        }
        for (int i = 0; i < rowCount; i++) {
            if (!model.getValueAt(i, 0).equals(settings.getCustomPrompts().get(i).getDescription())
                    || !model.getValueAt(i, 1).equals(settings.getCustomPrompts().get(i).getDescription())) {
                return true;
            }
        }
        return false;
    }

    private boolean isCustomPromptModified() {
        int selectedRow = ui.getSELECTED_ROW();
        DefaultTableModel model = (DefaultTableModel) ui.getCustomPromptsTable().getModel();
        int tableRowCount = model.getRowCount();

        if (selectedRow >= tableRowCount) {
            return true;
        }

        return !model.getValueAt(selectedRow, 0).equals(settings.getCustomPrompt().getDescription())
                || !model.getValueAt(selectedRow, 1).equals(settings.getCustomPrompt().getDescription());
    }
}