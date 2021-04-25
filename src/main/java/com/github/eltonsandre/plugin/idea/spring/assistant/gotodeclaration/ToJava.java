package com.github.eltonsandre.plugin.idea.spring.assistant.gotodeclaration;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.annotation.SpringAnnotationEnum;
import com.google.common.collect.Lists;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;


public abstract class ToJava {

    public static final PsiElement[] DEFAULT_RESULT = new PsiElement[0];

    public static PsiElement[] getElements(@NotNull final PsiElement sourceElement, final String configFullName) {
        final Project project = sourceElement.getProject();
        final Collection<VirtualFile> files = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        if (CollectionUtils.isEmpty(files)) {
            return DEFAULT_RESULT;
        }
        final ArrayList<PsiElement> result = Lists.newArrayList();
        for (final VirtualFile file : files) {
            final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

            final Collection<PsiAnnotation> childrenOfType = PsiTreeUtil.findChildrenOfType(psiFile, PsiAnnotation.class);
            if (CollectionUtils.isEmpty(childrenOfType)) {
                continue;
            }

            for (final PsiAnnotation psiAnnotation : childrenOfType) {
                if (!SpringAnnotationEnum.contains(psiAnnotation.getQualifiedName())) {
                    continue;
                }

                final PsiNameValuePair[] attributes1 = psiAnnotation.getParameterList().getAttributes();
                for (final PsiNameValuePair psiNameValuePair : attributes1) {
                    final JvmAnnotationAttributeValue attributeValue = psiNameValuePair.getAttributeValue();

                    if (checkEquals(configFullName, attributeValue)) {
                        result.add(new ThisPsiAnnotation(psiAnnotation.getNode()));
                        break;
                    }
                }
            }
        }

        final PsiElement[] psiElements = new PsiElement[result.size()];
        return result.toArray(psiElements);
    }

    private static boolean checkEquals(final String configFullName, final JvmAnnotationAttributeValue constantValue) {
        if (constantValue instanceof JvmAnnotationConstantValue) {
            final Object constantValue1 = ((JvmAnnotationConstantValue) constantValue).getConstantValue();

            String valueKey = Objects.requireNonNull(constantValue1).toString();

            if (LinkUtils.isPlaceholderContainer(valueKey)) {
                valueKey = LinkUtils.getKeyToPlaceholder(valueKey);
            }

            return Objects.equals(valueKey, configFullName);
        }

        if (constantValue instanceof JvmAnnotationArrayValue) {
            final JvmAnnotationArrayValue attributeValue1 = (JvmAnnotationArrayValue) constantValue;
            final List<JvmAnnotationAttributeValue> annotationArrayValues = attributeValue1.getValues();

            for (final JvmAnnotationAttributeValue value : annotationArrayValues) {
                if (checkEquals(configFullName, value)) {
                    return true;
                }
            }

        }

        return false;
    }

}
