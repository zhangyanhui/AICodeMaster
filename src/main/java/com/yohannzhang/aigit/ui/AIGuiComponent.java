package com.yohannzhang.aigit.ui;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

public class AIGuiComponent implements ProjectComponent {
    private final Project project;
    private CombinedWindowFactory windowFactory;

    public AIGuiComponent(Project project) {
        this.project = project;
    }

    public void setWindowFactory(CombinedWindowFactory factory) {
        this.windowFactory = factory;
    }

    public CombinedWindowFactory getWindowFactory() {
        return windowFactory;
    }

    public static AIGuiComponent getInstance(Project project) {

        return project.getService(AIGuiComponent.class);
    }
}
