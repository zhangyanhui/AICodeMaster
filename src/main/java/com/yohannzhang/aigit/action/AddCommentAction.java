package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.yohannzhang.aigit.handler.CommonMessageGenerator;
import com.yohannzhang.aigit.util.CodeUtil;

public class AddCommentAction extends AnAction {
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
        return "请基于以下 Java 方法生成 JUnit 5 单元测试，并使用 AssertJ 提供的断言方式编写更具可读性的测试逻辑。" +
                "请覆盖主要业务路径及异常情况。\n\n" +
                "目标代码如下：\n" + code;
    }


    private void showWarningDialog(String message) {
        Messages.showMessageDialog(message, "Warning", Messages.getWarningIcon());
    }


}