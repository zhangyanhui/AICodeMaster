package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class FindProblemsAction extends AnAction {

    private final String selectedText;

    public FindProblemsAction(String selectedText) {
        this.selectedText = selectedText;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            // 这里可以调用具体的查找问题的逻辑
            Messages.showMessageDialog(project, "Finding problems in code: " + selectedText, "Find Problems", Messages.getInformationIcon());
        }
    }
}