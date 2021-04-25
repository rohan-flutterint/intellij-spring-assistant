package com.github.eltonsandre.plugin.idea.spring.assistant.gotodeclaration;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PropertyNavigationItem extends PropertyImpl implements NavigationItem {

    public PropertyNavigationItem(@NotNull final ASTNode node) {
        super(node);
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            private static final int PRESENTABLE_TEXT_LENGTH = 100;

            @Override
            public String getLocationString() {
                return PropertyNavigationItem.this.getContainingFile().getName();
            }

            @Override
            public String getPresentableText() {
                return LinkUtils.truncateStringWithEllipsis(PropertyNavigationItem.this.getValue(), PRESENTABLE_TEXT_LENGTH);
            }

            @Override
            public @Nullable Icon getIcon(final boolean unused) {
                return PlatformIcons.PROPERTY_ICON;
            }
        };
    }
}
