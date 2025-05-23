package com.yohannzhang.aigit.ui;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.yohannzhang.aigit.ui.editor.CodeActionEditorListener;

public class AIGuiComponent implements ProjectComponent {
    private final Project project;
    private CombinedWindowFactory windowFactory;

    public AIGuiComponent(Project project) {
        this.project = project;
        // 注册编辑器监听器
//        CodeActionEditorListener.install(project);
    }

    public void setWindowFactory(CombinedWindowFactory factory) {
        this.windowFactory = factory;
    }

    public CombinedWindowFactory getWindowFactory() {
        return windowFactory;
    }

    public static AIGuiComponent getInstance(Project project) {

        return project.getService(AIGuiComponent.class);
    }

    public void analyzeSelectedCode(String selectedCode, String action) {
        String prompt;
        switch (action) {
            case "查找问题":
                prompt = String.format("请分析以下代码中可能存在的问题，包括但不限于：性能问题、安全隐患、代码规范问题等。请给出具体的问题描述和改进建议。\n\n代码：\n%s", selectedCode);
                break;
            case "优化代码":
                prompt = String.format("请对以下代码进行优化，包括但不限于：性能优化、代码结构优化、可读性优化等。请给出优化后的代码和优化说明。\n\n代码：\n%s", selectedCode);
                break;
            case "重构代码":
                prompt = String.format("请对以下代码进行重构，使其更加符合设计模式和最佳实践。请给出重构后的代码和重构说明。\n\n代码：\n%s", selectedCode);
                break;
            default:
                prompt = String.format("请分析以下代码：\n%s", selectedCode);
        }

        if (windowFactory != null) {
            windowFactory.updateResult(prompt);
        }
    }
}
