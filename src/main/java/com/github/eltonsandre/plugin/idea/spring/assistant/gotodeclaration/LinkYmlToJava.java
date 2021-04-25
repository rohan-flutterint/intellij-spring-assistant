package com.github.eltonsandre.plugin.idea.spring.assistant.gotodeclaration;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.Constants;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLPsiElement;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.util.Objects;

public final class LinkYmlToJava extends ToJava {


    public static PsiElement[] toJava(@NotNull final PsiElement sourceElement) {
        final var yamlPsiElement = PsiTreeUtil.getParentOfType(sourceElement, YAMLPsiElement.class);

        if (Objects.isNull(yamlPsiElement)) {
            return PsiElement.EMPTY_ARRAY;
        }

        final String configFullName = YAMLUtil.getConfigFullName(yamlPsiElement);
        final Pair<PsiElement, String> value = YAMLUtil.getValue((YAMLFile) sourceElement.getContainingFile(), configFullName.split(Constants.REGEX_DOT));

        if (Objects.isNull(value) || !(value.getFirst() instanceof YAMLPlainTextImpl)) {
            return DEFAULT_RESULT;
        }

        return getElements(sourceElement, configFullName);
    }

}
