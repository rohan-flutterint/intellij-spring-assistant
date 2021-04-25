package com.github.eltonsandre.plugin.idea.spring.assistant.gotodeclaration;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;

import javax.swing.*;

class ThisYAMLKeyValueImpl extends YAMLKeyValueImpl implements YAMLKeyValue {

    public ThisYAMLKeyValueImpl(@NotNull final ASTNode node) {
        super(node);
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            private static final int PRESENTABLE_TEXT_LENGTH = 100;

            @Override
            public String getLocationString() {
                return ThisYAMLKeyValueImpl.this.getContainingFile().getName();
            }

            @Override
            public String getPresentableText() {
                assert ThisYAMLKeyValueImpl.this.getValue() != null;
                final String configFullName = YAMLUtil.getConfigFullName(ThisYAMLKeyValueImpl.this.getValue());
                final String keyValues = configFullName + ": " + ThisYAMLKeyValueImpl.this.getValue().getText();
                return LinkUtils.truncateStringWithEllipsis(keyValues, PRESENTABLE_TEXT_LENGTH);
            }

            @Override
            public @Nullable Icon getIcon(final boolean unused) {
                return PlatformIcons.PROPERTY_ICON;
            }

        };
    }

}
