package com.yohannzhang.aigit.action;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.yohannzhang.aigit.handler.CommonMessageGenerator;
import com.yohannzhang.aigit.service.CodeService;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import com.yohannzhang.aigit.util.IdeaDialogUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DiffCodeReviewAction extends AnAction {
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";

    private static final String DEFAULT_TEXT = "AI Code Review";
    private static final String PROCESSING_TEXT = "Reviewing...";
    private volatile boolean isProcessing = false;
    private static final Logger LOG = Logger.getInstance(DiffCodeReviewAction.class);
    private final CodeService codeService = new CodeService();
    private final StringBuilder messageBuilder = new StringBuilder();

    public DiffCodeReviewAction() {
        super(DEFAULT_TEXT, "Perform AI code review on diff", AllIcons.Actions.Show);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Presentation presentation = e.getPresentation();
        
        // 添加调试日志
        LOG.info("DiffCodeReviewAction update called");
        LOG.info("Project: " + (project != null ? "exists" : "null"));
        LOG.info("IsProcessing: " + isProcessing);
        
        // 检查是否在 Diff 上下文中
        DiffRequest request = e.getData(DiffDataKeys.DIFF_REQUEST);
        LOG.info("DiffRequest: " + (request != null ? request.getClass().getName() : "null"));
        
        // 检查 Action 的上下文
        LOG.info("Place: " + e.getPlace());
        LOG.info("InputEvent: " + (e.getInputEvent() != null ? e.getInputEvent().getClass().getName() : "null"));
        LOG.info("DataContext: " + e.getDataContext().getClass().getName());
        
        // 简化条件：只要有项目且不在处理中，就显示按钮
        boolean shouldShow = project != null && !isProcessing;
        LOG.info("Should show button: " + shouldShow);
        
        presentation.setEnabledAndVisible(shouldShow);
        presentation.setText(isProcessing ? PROCESSING_TEXT : DEFAULT_TEXT);
        
        LOG.info("Button enabled: " + presentation.isEnabled());
        LOG.info("Button visible: " + presentation.isVisible());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || isProcessing) {
            return;
        }

        DiffRequest request = e.getData(DiffDataKeys.DIFF_REQUEST);
        if (request == null) {
            LOG.info("No diff request found");
            IdeaDialogUtil.showWarning(project, "No diff content found.", "Code Review");
            return;
        }

        List<DiffContent> contents;

        if (request instanceof SimpleDiffRequest) {
            contents = ((SimpleDiffRequest) request).getContents();
        } else if (request instanceof ContentDiffRequest) {
            contents = ((ContentDiffRequest) request).getContents();
        } else {
            LOG.warn("Unknown DiffRequest type: " + request.getClass().getName());
            IdeaDialogUtil.showWarning(project, "Unsupported diff type", "Code Review");
            return;
        }

        if (contents.size() < 2) {
            LOG.info("At least two contents required for diff");
            IdeaDialogUtil.showWarning(project, "At least two contents required for diff", "Code Review");
            return;
        }

//        List<DiffContent> contents = ((SimpleDiffRequest) request).getContents();
        if (contents.size() != 2) {
            LOG.info("Invalid number of contents: " + contents.size());
            return;
        }

        if (!(contents.get(0) instanceof DocumentContent) || !(contents.get(1) instanceof DocumentContent)) {
            LOG.info("Not all contents are DocumentContent");
            IdeaDialogUtil.showWarning(project, "Only text diff is supported.", "Code Review");
            return;
        }

        DocumentContent leftContent = (DocumentContent) contents.get(0);
        DocumentContent rightContent = (DocumentContent) contents.get(1);

        String diff = buildDiff(leftContent.getDocument(), rightContent.getDocument());
        //print

        if (diff.isEmpty()) {
            LOG.info("No changes found in diff");
            IdeaDialogUtil.showWarning(project, "No changes to review.", "Code Review");
            return;
        }

        isProcessing = true;
        update(e);

//        CodeService codeService = new CodeService();
        String prompt = String.format(
                "请对以下代码 diff 进行 Code Review，并回答下列问题：\n" +
                "\n" +
                "### 1. \uD83E\uDDE0 总体理解\n" +
                "- `diff` 中的更改解决了什么问题？是否清晰表达了意图？\n" +
                "- 是否有足够的上下文说明为什么需要这次更改？\n" +
                "\n" +
                "### 2. ✅ 功能性\n" +
                "- 更改是否实现了预期功能？\n" +
                "- 是否遗漏了边界条件、异常处理或输入验证？\n" +
                "- 是否存在潜在的运行时错误或空指针风险？\n" +
                "\n" +
                "### 3. \uD83D\uDD10 安全性\n" +
                "- 是否涉及敏感数据操作（如密码、token、文件读写等）？是否有安全风险？\n" +
                "- 是否对外部输入进行了校验和过滤？\n" +
                "\n" +
                "### 4. \uD83D\uDE80 性能与效率\n" +
                "- 是否存在性能瓶颈（如循环中调用数据库、频繁 GC 对象创建等）？\n" +
                "- 是否有不必要的计算或重复逻辑？\n" +
                "\n" +
                "### 5. \uD83D\uDCC1 可维护性\n" +
                "- 代码风格是否统一？是否符合项目编码规范？\n" +
                "- 是否缺乏必要的注释或文档更新？\n" +
                "- 是否可以进一步解耦或模块化？\n" +
                "\n" +
                "### 6. \uD83D\uDD04 测试覆盖\n" +
                "- 是否有新增或修改测试用例来覆盖本次变更？\n" +
                "- 是否存在未被测试的关键路径？\n" +
                "\n" +
                "### 7. \uD83D\uDD0D 潜在缺陷\n" +
                "- 是否引入了并发问题（如线程安全）？\n" +
                "- 是否影响到其他模块或接口？\n" +
                "\n" +
                "### 8. \uD83D\uDCCC 其他建议\n" +
                "- 是否有更好的实现方式或设计模式可以应用？\n" +
                "- 是否有重复代码可以抽取为公共方法？\n" +
                "\n" +
                "---\n" +
                "\n" +
                "> \uD83D\uDCA1 示例格式：\n" +
                "```text\n" +
                "[问题点]：在 `calculateTotalPrice()` 方法中没有处理 null 的情况。\n" +
                "[建议]：增加 null check 并抛出合适的异常。\n" +

                "用中文回答，代码改动如下：\n%s", diff);

        StringBuilder reviewResult = new StringBuilder();
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow == null) {
            return;
        }
        toolWindow.show(() -> {
        });
        LOG.info("prompt: " + prompt);
        generate(prompt,project,e);
    }

    public void generate(String prompt,Project project,AnActionEvent ae) {
        messageBuilder.setLength(0);
        try {
            codeService.generateCommitMessageStream(
                    prompt,
                    token -> handleTokenResponse(token,project),
                    this::handleErrorResponse,
                    () -> ApplicationManager.getApplication().invokeLater(() -> {
//                        isProcessing= false;
                       resetState(ae);
                        CombinedWindowFactory.getInstance(project).resetButton(project);
                    })
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleTokenResponse(String token,Project project) {
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

        });
    }

    private String buildDiff(Document leftDoc, Document rightDoc) {
        StringBuilder diff = new StringBuilder();
        String leftText = leftDoc.getText();
        String rightText = rightDoc.getText();

        // 简单的行比较
        String[] leftLines = leftText.split("\n");
        String[] rightLines = rightText.split("\n");

        int i = 0, j = 0;
        while (i < leftLines.length || j < rightLines.length) {
            if (i < leftLines.length && j < rightLines.length) {
                if (!leftLines[i].equals(rightLines[j])) {
                    diff.append("- ").append(leftLines[i]).append("\n");
                    diff.append("+ ").append(rightLines[j]).append("\n");
                }
                i++;
                j++;
            } else if (i < leftLines.length) {
                diff.append("- ").append(leftLines[i]).append("\n");
                i++;
            } else {
                diff.append("+ ").append(rightLines[j]).append("\n");
                j++;
            }
        }

        return diff.toString();
    }

    private void resetState(AnActionEvent e) {
        isProcessing = false;
        update(e);
    }
} 