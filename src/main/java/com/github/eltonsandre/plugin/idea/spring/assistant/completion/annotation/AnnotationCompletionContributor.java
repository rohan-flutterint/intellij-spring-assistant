package com.github.eltonsandre.plugin.idea.spring.assistant.completion.annotation;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.patterns.PlatformPatterns;

public class AnnotationCompletionContributor extends CompletionContributor {

    public AnnotationCompletionContributor() {
        this.extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(JavaLanguage.INSTANCE),
                new AnnotationCompletionProvider());
    }


}
