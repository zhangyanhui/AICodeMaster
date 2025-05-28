package com.yohannzhang.aigit.handler;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import com.yohannzhang.aigit.util.IdeaDialogUtil;

public class CommonMessageGenerator {
    private final Project project;
    private final CodeService codeService = new CodeService();
    private final StringBuilder messageBuilder = new StringBuilder();
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";


    public CommonMessageGenerator(Project project) {
        this.project = project;
//        this.codeService = codeService;
    }

    public void generate(String prompt) {
        messageBuilder.setLength(0);
        try {
            codeService.generateCommitMessageStream(
                    prompt,
                    token -> handleTokenResponse(token),
                    this::handleErrorResponse,
                    () -> ApplicationManager.getApplication().invokeLater(() -> {
                        CombinedWindowFactory.getInstance(project).resetButton(project);
                    })
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleTokenResponse(String token) {
        ApplicationManager.getApplication().invokeLater(() -> {
            messageBuilder.append(token);
            CombinedWindowFactory.getInstance(project)
                    .updateResult(messageBuilder.toString(), project);
            CombinedWindowFactory.getInstance(project)
                    .submitButton(project);
        });
    }

    private void handleErrorResponse(Throwable error) {
        ApplicationManager.getApplication().invokeLater(() -> {
            IdeaDialogUtil.showError(project,
                    "Error generating commit message: " + error.getMessage(),
                    "Error");
        });
    }
}
