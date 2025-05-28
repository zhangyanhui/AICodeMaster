package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.yohannzhang.aigit.constant.Constants;
import com.yohannzhang.aigit.handler.CommonMessageGenerator;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import com.yohannzhang.aigit.util.CodeUtil;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import org.jetbrains.annotations.NotNull;

public class GenJUintTestAction extends AnAction {
    //    private final CommonMessageGenerator commonMessageGenerator = new CommonMessageGenerator();
    private static final CodeUtil CODE_UTIL = new CodeUtil();
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";
    private final StringBuilder messageBuilder = new StringBuilder();

    @Override
    public void actionPerformed(AnActionEvent event) {
//        if (!ActionControl.startAction()) {
//            Messages.showMessageDialog("正在处理任务，请稍后再试.", "Warning", Messages.getWarningIcon());
//            return;
//        }
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            showWarningDialog("No active editor found!");
            return;
        }

//        final String selectedText = editor.getSelectionModel().getSelectedText();
        // 获取当前文件代码
        String selectedText = editor.getDocument().getText();


//        final String selectedText =  editor.getDocument().getText();
        if (selectedText == null || selectedText.isEmpty()) {
            showWarningDialog("No code selected!");
            return;
        }

        Project project = event.getProject();
        if (project == null) {
            return;
        }


        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow == null) {
            return;
        }

        toolWindow.show(() -> {
        });
        CommonMessageGenerator commonMessageGenerator = new CommonMessageGenerator(project);
        commonMessageGenerator.generate(buildPrompt(selectedText));
//        processSelectedCode(project, selectedText);
    }



    private String buildPrompt(String code) {
        return "为以下代码生成单元测试方法，" +
                "代码如下：" + code;
    }



    private void showWarningDialog(String message) {
        Messages.showMessageDialog(message, "Warning", Messages.getWarningIcon());
    }


}