package com.yohannzhang.aigit.ui.editor;

import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.yohannzhang.aigit.handler.CommonMessageGenerator;
import com.yohannzhang.aigit.ui.AIGuiComponent;
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
        
        actionGroup.add(new AnAction("查找问题") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                String promt = "请对以下java代码进行深度分析，重点关注以下方面： **分析维度**：\n" +
                        "\n" +
                        "1. **基础错误检测**\n" +
                        "   - 语法错误（缺少分号/括号等）\n" +
                        "   - 类型不匹配（如String转int）\n" +
                        "   - 未处理异常（try-catch缺失）\n" +
                        "\n" +
                        "2. **代码质量评估**\n" +
                        "   - [圈复杂度] >15的方法：标记`methodName`\n" +
                        "   - [重复代码] 相似度≥80%的代码块\n" +
                        "   - [代码异味] 如过长参数列表(>5个)\n" +
                        "\n" +
                        "3. **潜在风险扫描**\n" +
                        "   - 资源泄漏（未关闭的IO流）\n" +
                        "   - 线程安全问题（非同步集合）\n" +
                        "   - 空指针风险（未做null检查）\n" +
                        "\n" +
                        "4. **性能优化建议**\n" +
                        "   - 高频循环中的低效操作\n" +
                        "   - 不必要的对象创建\n" +
                        "   - 可缓存的计算结果\n" +
                        "\n" +
                        "5. **安全漏洞检查**\n" +
                        "   - 硬编码凭证\n" +
                        "   - SQL注入风险\n" +
                        "   - XXE漏洞\n" +"代码如下：";

                analyzeCode(promt);
            }
        });
        
        actionGroup.add(new AnAction("优化代码") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                String promt = "# Java代码优化分析提示词\n" +
                        "\n" +
                        "请对以下Java代码进行全面优化分析，重点从以下维度提供具体优化建议：\n" +
                        "\n" +
                        "1. **性能瓶颈检测**\n" +
                        "   - 高频循环中的时间复杂度优化（如O(n²)→O(n)）\n" +
                        "   - 内存泄漏风险点（如未释放的`InputStream`）\n" +
                        "   - 冗余对象创建（特别是在循环内）\n" +
                        "\n" +
                        "2. **代码结构改进**\n" +
                        "   - 过长的方法（>50行）拆分建议\n" +
                        "   - 重复代码块提取为独立方法\n" +
                        "   - 多条件嵌套逻辑简化策略\n" +
                        "\n" +
                        "3. **现代Java特性应用**\n" +
                        "   - 可用Stream API替代的传统循环\n" +
                        "   - 可替换为`var`的显式类型声明\n" +
                        "   - Optional使用的改进建议\n" +
                        "\n" +
                        "4. **设计模式优化**\n" +
                        "   - 识别适合引入策略/工厂模式的地方\n" +
                        "   - 观察者模式优化的消息通知机制\n" +
                        "   - 单例模式的双重检查锁定实现检查\n" +
                        "\n" +
                        "5. **并发性能提升**\n" +
                        "   - `synchronized`块优化建议\n" +
                        "   - 线程池配置合理性评估\n" +
                        "   - 并发集合（如`ConcurrentHashMap`）使用建议\n" +
                        "\n" +
                        "6. **可读性增强**\n" +
                        "   - 模糊变量名重构建议（如`temp`→`userCache`）\n" +
                        "   - 魔法数字替换为常量\n" +
                        "   - 复杂布尔表达式分解\n" +
                        "\n" +
                        "请按以下格式反馈：\n" +
                        "- 问题定位：`类名#方法名(行号)`\n" +
                        "- 原代码片段：`代码摘录`\n" +
                        "- 优化方案：具体修改建议\n" +
                        "- 预期收益：性能指标/可维护性提升说明\n" +
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
                        "请对以下Java代码进行深度重构分析，重点从以下维度提供重构建议：\n" +
                        "\n" +
                        "1. **代码坏味道检测**\n" +
                        "   - 过长方法（>30行）标记并给出拆分方案\n" +
                        "   - 过大类（>500行）建议职责分解\n" +
                        "   - 重复代码（相似度≥70%）提取方案\n" +
                        "   - 过长参数列表（>5个）封装建议\n" +
                        "\n" +
                        "2. **面向对象优化**\n" +
                        "   - 违反单一职责原则的类标记\n" +
                        "   - 适合提取接口/抽象类的场景\n" +
                        "   - 继承关系优化（用组合替代继承）\n" +
                        "   - 多态性改进点\n" +
                        "\n" +
                        "3. **设计模式应用**\n" +
                        "   - 识别适合引入的设计模式场景：\n" +
                        "     * 工厂模式（复杂对象创建）\n" +
                        "     * 策略模式（算法替换）\n" +
                        "     * 观察者模式（事件通知）\n" +
                        "     * 装饰器模式（动态扩展）\n" +
                        "\n" +
                        "4. **可维护性提升**\n" +
                        "   - 魔法数字/字符串常量化建议\n" +
                        "   - 复杂条件逻辑简化策略\n" +
                        "   - 嵌套过深代码（>3层）扁平化方案\n" +
                        "   - 注释驱动重构（过期注释对应的代码）\n" +
                        "\n" +
                        "5. **API设计改进**\n" +
                        "   - 方法签名不合理之处（如布尔参数）\n" +
                        "   - 返回类型模糊的方法（返回Object）\n" +
                        "   - 异常处理规范化建议\n" +
                        "\n" +
                        "6. **测试友好性**\n" +
                        "   - 静态方法依赖问题\n" +
                        "   - 紧耦合代码的Mock困难点\n" +
                        "   - 不可测试的私有方法\n" +
                        "\n" +
                        "请按以下格式反馈：\n" +
                        "1. 坏味道类型： [类型]（如Long Method）\n" +
                        "2. 代码位置： `ClassName#methodName()` (行号)\n" +
                        "3. 重构方案： 具体重构步骤说明\n" +
                        "4. 代码示例： 重构前后代码对比\n" +
                        "5. 收益评估： 可维护性/可扩展性提升点\n" +
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

        toolWindow.show(() -> {});
        promt = promt+selectedText;
        CommonMessageGenerator commonMessageGenerator = new CommonMessageGenerator(project);
        commonMessageGenerator.generate(promt);

    }
} 