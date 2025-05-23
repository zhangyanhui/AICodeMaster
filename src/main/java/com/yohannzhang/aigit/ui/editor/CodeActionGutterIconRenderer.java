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
                analyzeCode("查找问题");
            }
        });
        
        actionGroup.add(new AnAction("优化代码") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                analyzeCode("优化代码");
            }
        });
        
        actionGroup.add(new AnAction("重构代码") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                analyzeCode("重构代码");
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

    private void analyzeCode(String action) {
        SelectionModel selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            return;
        }

        String selectedText = selectionModel.getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            return;
        }

        // 获取 AIGuiComponent 实例并调用相应的方法
        AIGuiComponent.getInstance(project).analyzeSelectedCode(selectedText, action);
    }
} 