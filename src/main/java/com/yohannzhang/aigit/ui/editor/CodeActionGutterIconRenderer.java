package com.yohannzhang.aigit.ui.editor;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.yohannzhang.aigit.handler.CommonMessageGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Objects;

public class CodeActionGutterIconRenderer extends GutterIconRenderer {
    private final Editor editor;
    private final Project project;
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";

    public static final Icon DEFAULT_ICON = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 绘制圆形背景
            g2d.setColor(new Color(74, 144, 226)); // #4A90E2
            g2d.fillOval(x, y, 16, 16);

            // 绘制文字
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 10));
            FontMetrics fm = g2d.getFontMetrics();
            String text = "AI";
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            g2d.drawString(text, x + (16 - textWidth) / 2, y + (16 + textHeight) / 2 - 2);

            g2d.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    };

    public CodeActionGutterIconRenderer(Editor editor, Project project) {
        this.editor = editor;
        this.project = project;
    }

    @Override
    public @NotNull Icon getIcon() {
        Icon icon = IconLoader.getIcon("/icons/code_action.svg", CodeActionGutterIconRenderer.class);
        return icon != null ? icon : DEFAULT_ICON;
    }

    @Override
    public @Nullable String getTooltipText() {
        return "AI Code Actions";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CodeActionGutterIconRenderer that = (CodeActionGutterIconRenderer) obj;
        return Objects.equals(editor, that.editor) && Objects.equals(project, that.project);
    }

    @Override
    public int hashCode() {
        return Objects.hash(editor, project);
    }

    @Override
    public AnAction getClickAction() {
        return new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                MouseEvent mouseEvent = (MouseEvent) e.getInputEvent();
                showPopup(e.getDataContext(), mouseEvent.getX(), mouseEvent.getY());
            }
        };
    }

    private void showPopup(com.intellij.openapi.actionSystem.DataContext dataContext, int x, int y) {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        actionGroup.add(new AnAction("补充注释") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                String promt = "# Java代码逐行注释提示词\n" +
                        "\n" +
                        "请为以下Java代码逐行添加注释说明，要求包括：\n" +
                        "\n" +
                        "1. 逐行解释\n" +
                        "   - 每一行代码的功能和作用\n" +
                        "   - 如果是控制结构（if/for/while等），说明其逻辑意图\n" +
                        "\n" +
                        "2. 变量说明\n" +
                        "   - 关键变量的用途及生命周期\n" +
                        "   - 特殊值或状态标识的含义\n" +
                        "\n" +
                        "3. 方法调用说明\n" +
                        "   - 调用了哪些外部方法？其作用是什么？\n" +
                        "   - 是否有副作用或抛出异常？\n" +
                        "\n" +
                        "4. 性能与安全关注点（如适用）\n" +
                        "   - 是否存在资源泄漏、线程安全问题\n" +
                        "   - 是否影响性能（如频繁GC、IO操作）\n" +
                        "\n" +
                        "格式要求：\n" +
                        "- 注释应紧贴对应代码行，使用[//](file:///Users/yanhuizhang/IdeaProjects/AICodeMaster/LICENSE)风格\n" +
                        "- 保持语言简洁明了，避免冗余描述\n" +
                        "- 不修改原代码结构，仅添加注释\n" +
                        "\n" +
                        "待注释代码如下：\n";


                analyzeCode(promt);
            }
        });

        actionGroup.add(new AnAction("优化代码") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                String promt = "# Java代码优化提示词\n" +
                        "\n" +
                        "请对以下代码提供优化建议，重点包括：\n" +
                        "\n" +
                        "1. 性能瓶颈\n" +
                        "   - 时间复杂度、内存泄漏、循环内创建对象\n" +
                        "\n" +
                        "2. 结构改进\n" +
                        "   - 方法拆分、提取公共逻辑、简化嵌套\n" +
                        "\n" +
                        "3. 新特性应用\n" +
                        "   - Stream API、var关键字、Optional使用\n" +
                        "\n" +
                        "4. 并发优化\n" +
                        "   - synchronized优化、线程池配置、并发集合\n" +
                        "\n" +
                        "5. 可读性增强\n" +
                        "   - 变量重命名、魔法数常量化、布尔表达式分解\n" +
                        "\n" +
                        "格式要求：\n" +
                        "- 问题定位：`类名#方法名(行号)`\n" +
                        "- 原代码片段：`代码摘录`\n" +
                        "- 优化方案：具体修改建议\n" +
                        "- 预期收益：性能/可维护性说明\n" +
                        "\n" +
                        "待优化代码如下：\n";

                analyzeCode(promt);
            }
        });

        actionGroup.add(new AnAction("重构代码") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                String promt = "# Java代码重构提示词\n" +
                        "\n" +
                        "请对以下代码提供重构建议，重点包括：\n" +
                        "\n" +
                        "1. 坏味道识别\n" +
                        "   - 长方法（>30行）、大类（>500行）、重复代码（≥70%）\n" +
                        "   - 长参数列表封装建议\n" +
                        "\n" +
                        "2. OOP优化\n" +
                        "   - 单一职责、接口抽象、组合替代继承\n" +
                        "\n" +
                        "3. 设计模式应用\n" +
                        "   - 工厂、策略、观察者、装饰器等适用场景\n" +
                        "\n" +
                        "4. 可维护性提升\n" +
                        "   - 常量化、条件简化、嵌套扁平化、注释驱动重构\n" +
                        "\n" +
                        "5. API设计改进\n" +
                        "   - 方法签名、返回类型、异常处理\n" +
                        "\n" +
                        "6. 测试友好性\n" +
                        "   - 静态依赖、Mock困难点、私有方法测试\n" +
                        "\n" +
                        "格式要求：\n" +
                        "1. 坏味道类型：[如Long Method]\n" +
                        "2. 位置：`ClassName#methodName()` (行号)\n" +
                        "3. 方案：具体步骤\n" +
                        "4. 示例：重构前后对比\n" +
                        "5. 收益：可维护性/扩展性说明\n" +
                        "\n" +
                        "待重构代码如下：\n";


                analyzeCode(promt);
            }
        });

        ListPopup popup = JBPopupFactory.getInstance()
                .createActionGroupPopup(
                        "AI Code Actions",
                        actionGroup,
                        dataContext,
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                        true
                );

        popup.showInBestPositionFor(dataContext);
    }

    private void analyzeCode(String promt) {
        SelectionModel selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            return;
        }

        String selectedText = selectionModel.getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            return;
        }
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow == null) {
            return;
        }

        toolWindow.show(() -> {
        });
        promt = promt + selectedText;
        CommonMessageGenerator commonMessageGenerator = new CommonMessageGenerator(project);
        commonMessageGenerator.generate(promt);

    }
} 