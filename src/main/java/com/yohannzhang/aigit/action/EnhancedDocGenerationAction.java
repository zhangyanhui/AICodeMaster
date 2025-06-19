package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class EnhancedDocGenerationAction extends DefaultActionGroup {
    public EnhancedDocGenerationAction() {
        super("生成文档", true);
        add(new GenerateProjectDocAction());
//        add(new GenerateApiDocAction());
//        add(new GenerateUmlDiagramAction());
        add(new GenerateDependencyGraphAction());
        add(new GenerateBilingualDocAction());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabledAndVisible(true);
    }
} 