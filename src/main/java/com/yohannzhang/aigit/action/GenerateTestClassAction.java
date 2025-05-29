//package com.yohannzhang.aigit.action;
//
//import com.intellij.openapi.actionSystem.AnAction;
//import com.intellij.openapi.actionSystem.AnActionEvent;
//import com.intellij.openapi.actionSystem.CommonDataKeys;
//import com.intellij.openapi.editor.Editor;
//import com.intellij.openapi.project.Project;
//import com.intellij.psi.PsiClass;
//import com.intellij.psi.PsiElement;
//import com.intellij.psi.PsiFile;
//import com.intellij.psi.util.PsiTreeUtil;
//import com.yohannzhang.aigit.service.TestClassGeneratorService;
//import com.yohannzhang.aigit.ui.CombinedWindowFactory;
//import org.jetbrains.annotations.NotNull;
//
//public class GenerateTestClassAction extends AnAction {
//    @Override
//    public void actionPerformed(@NotNull AnActionEvent e) {
//        Project project = e.getProject();
//        if (project == null) return;
//
//        Editor editor = e.getData(CommonDataKeys.EDITOR);
//        if (editor == null) return;
//
//        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
//        if (psiFile == null) return;
//
//        // 获取当前光标位置的类
//        int offset = editor.getCaretModel().getOffset();
//        PsiElement element = psiFile.findElementAt(offset);
//        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
//
//        if (psiClass == null) {
//            return;
//        }
//
//        // 创建测试类生成服务
//        TestClassGeneratorService service = new TestClassGeneratorService(project, psiClass);
//
//        // 获取输出面板
//        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
//        if (windowFactory == null) return;
//
//        // 生成测试类
//        service.generateTestClass(new TestClassGeneratorService.TestGenerationCallback() {
//            @Override
//            public void onTokenReceived(String token) {
//                windowFactory.updateResult(token, project);
//            }
//
//            @Override
//            public void onError(Throwable error) {
//                windowFactory.updateResult("生成测试类时发生错误: " + error.getMessage(), project);
//            }
//
//            @Override
//            public void onComplete() {
//                // 生成完成后的处理
//            }
//        });
//    }
//
//    @Override
//    public void update(@NotNull AnActionEvent e) {
//        Project project = e.getProject();
//        Editor editor = e.getData(CommonDataKeys.EDITOR);
//        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
//
//        boolean enabled = project != null && editor != null && psiFile != null;
//        e.getPresentation().setEnabledAndVisible(enabled);
//    }
//}