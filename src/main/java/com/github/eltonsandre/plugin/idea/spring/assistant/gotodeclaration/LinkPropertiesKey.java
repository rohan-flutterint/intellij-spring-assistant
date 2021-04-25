package com.github.eltonsandre.plugin.idea.spring.assistant.gotodeclaration;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLLanguage;


public class LinkPropertiesKey implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable final PsiElement sourceElement, final int offset, final Editor editor) {
        if (sourceElement == null) {
            return null;
        }

        if (sourceElement instanceof PsiJavaToken) {
            return LinkGoToFilePropertieKey.toPropertieKey(sourceElement);
        }

        if (YAMLLanguage.INSTANCE.is(sourceElement.getLanguage())) {
            return LinkYmlToJava.toJava(sourceElement);
        }

        if (PropertiesLanguage.INSTANCE.is(sourceElement.getLanguage())) {
            return LinkPropertiesToJava.toJava(sourceElement);
        }

        return LinkYmlToJava.DEFAULT_RESULT;
    }

}
