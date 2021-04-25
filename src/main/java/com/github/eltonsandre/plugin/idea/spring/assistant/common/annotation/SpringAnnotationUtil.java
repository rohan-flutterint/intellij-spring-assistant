package com.github.eltonsandre.plugin.idea.spring.assistant.common.annotation;


import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpringAnnotationUtil {

    public static boolean isNotAnnotationBySpring(final PsiElement element) {
        return !isAnnotationBySpring(element);
    }

    public static boolean isAnnotationBySpring(final PsiElement element) {
        final var psiAnnotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);

        if (psiAnnotation == null) {
            return false;
        }

        return SpringAnnotationEnum.contains(psiAnnotation.getQualifiedName());
    }

}
