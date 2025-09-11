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

        actionGroup.add(new AnAction("Code Review") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                String promt = "# Java代码Code Review提示词\n" +
                        "\n" +
                        "请对以下代码进行Code Review，重点关注以下方面：\n" +
                        "\n" +
                        "1. **空指针异常检查**\n" +
                        "   - 方法参数是否进行null检查\n" +
                        "   - 对象调用前是否判断null\n" +
                        "   - 集合操作前是否检查空集合\n" +
                        "\n" +
                        "2. **内存溢出风险**\n" +
                        "   - 大对象创建和生命周期管理\n" +
                        "   - 循环中的对象创建\n" +
                        "   - 资源未正确释放（IO、数据库连接等）\n" +
                        "\n" +
                        "3. **线程安全问题**\n" +
                        "   - 共享变量的同步访问\n" +
                        "   - 死锁风险检查\n" +
                        "   - 原子操作的正确使用\n" +
                        "\n" +
                        "4. **异常处理**\n" +
                        "   - 异常捕获的粒度是否合适\n" +
                        "   - 是否有异常信息丢失\n" +
                        "   - 资源释放的finally处理\n" +
                        "\n" +
                        "5. **性能问题**\n" +
                        "   - 算法复杂度分析\n" +
                        "   - 不必要的重复计算\n" +
                        "   - 数据库查询优化\n" +
                        "\n" +
                        "**重要要求：对于发现的每个问题，必须同时提供：**\n" +
                        "1. 具体的代码行号定位\n" +
                        "2. 完整的源代码片段（包含上下文）\n" +
                        "3. 详细的问题描述和修复建议\n" +
                        "\n" +
                        "输出格式要求：\n" +
                        "❌ **[问题类型]** (第X-Y行): 问题详细描述\n" +
                        "\n" +
                        "**问题代码：**\n" +
                        "```java\n" +
                        "// 第X行开始\n" +
                        "完整的问题代码片段（包含足够的上下文）\n" +
                        "// 第Y行结束\n" +
                        "```\n" +
                        "\n" +
                        "**问题分析：** 详细说明为什么这段代码有问题\n" +
                        "\n" +
                        "✅ **修复建议：**\n" +
                        "```java\n" +
                        "// 修复后的代码\n" +
                        "完整的修复后代码片段\n" +
                        "```\n" +
                        "\n" +
                        "---\n" +
                        "\n" +
                        "示例输出：\n" +
                        "❌ **[空指针风险]** (第15-17行): 方法参数未进行null检查，直接调用trim()方法\n" +
                        "\n" +
                        "**问题代码：**\n" +
                        "```java\n" +
                        "// 第15行开始\n" +
                        "public void setUserName(String name) {\n" +
                        "    this.userName = name.trim(); // 第16行：可能抛出NullPointerException\n" +
                        "    System.out.println(\"用户名已设置: \" + this.userName); // 第17行\n" +
                        "}\n" +
                        "// 第18行结束\n" +
                        "```\n" +
                        "\n" +
                        "**问题分析：** 当name参数为null时，调用trim()方法会导致NullPointerException异常\n" +
                        "\n" +
                        "✅ **修复建议：**\n" +
                        "```java\n" +
                        "public void setUserName(String name) {\n" +
                        "    if (name == null) {\n" +
                        "        throw new IllegalArgumentException(\"用户名不能为null\");\n" +
                        "    }\n" +
                        "    this.userName = name.trim();\n" +
                        "    System.out.println(\"用户名已设置: \" + this.userName);\n" +
                        "}\n" +
                        "```\n" +
                        "\n" +
                        "待Review代码如下：\n";

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