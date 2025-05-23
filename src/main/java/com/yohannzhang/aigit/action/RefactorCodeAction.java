package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class RefactorCodeAction extends AnAction {

    private final String selectedText;

    public RefactorCodeAction(String selectedText) {
        this.selectedText = selectedText;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            // 这里可以调用具体的重构代码的逻辑
            Messages.showMessageDialog(project, "Refactoring code: " + selectedText, "Refactor Code", Messages.getInformationIcon());
        }
    }
}