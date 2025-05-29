package com.yohannzhang.aigit.handler;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NaturalLanguageCompletionHandler extends CodeCompletionHandlerBase {

    private final List<String> suggestions; // 建议列表

    public NaturalLanguageCompletionHandler(List<String> suggestions) {
        super(CompletionType.BASIC);
        this.suggestions = suggestions;
    }


    protected void collectData(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull CompletionResultSet result) {
        String prefix = getCurrentPrefix(editor);

        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.addElement(LookupElementBuilder.create(suggestion));
            }
        }

        result.stopHere();
    }

    private String getCurrentPrefix(Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        CharSequence text = editor.getDocument().getCharsSequence();
        int start = offset;

        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            start--;
        }

        return text.subSequence(start, offset).toString();
    }
}
