package com.yohannzhang.aigit.ui.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CodeActionEditorListener implements EditorMouseListener, SelectionListener, StartupActivity {
    private final List<RangeHighlighter> highlighters = new ArrayList<>();
    private Project project;

    @Override
    public void runActivity(@NotNull Project project) {
        this.project = project;
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(this, project);
        EditorFactory.getInstance().getEventMulticaster().addSelectionListener(this, project);
    }

    @Override
    public void mouseReleased(@NotNull EditorMouseEvent e) {
        handleSelection(e.getEditor());
    }

    @Override
    public void selectionChanged(@NotNull com.intellij.openapi.editor.event.SelectionEvent e) {
        handleSelection(e.getEditor());
    }

    private void handleSelection(Editor editor) {
        SelectionModel selectionModel = editor.getSelectionModel();
        clearHighlighters();
        if (selectionModel.hasSelection()) {
            int startLine = editor.getDocument().getLineNumber(selectionModel.getSelectionStart());
            showCodeActionButton(editor, startLine);
        }
    }

    private void showCodeActionButton(Editor editor, int line) {
        MarkupModel markupModel = editor.getMarkupModel();
        RangeHighlighter highlighter = markupModel.addLineHighlighter(
                line,
                HighlighterLayer.SELECTION - 1,
                new TextAttributes()
        );
        highlighter.setGutterIconRenderer(new CodeActionGutterIconRenderer(editor, project));
        highlighters.add(highlighter);
        editor.getComponent().repaint();
    }

    private void clearHighlighters() {
        for (RangeHighlighter highlighter : highlighters) {
            highlighter.dispose();
        }
        highlighters.clear();
    }
} 