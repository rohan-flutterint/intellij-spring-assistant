package com.github.eltonsandre.plugin.idea.spring.assistant.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.swing.*;

import static com.intellij.openapi.util.IconLoader.getIcon;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Icons {

    public static final Icon springBoot = getIcon("/icons/spring-boot.svg", Icons.class);
    public static final Icon springCloud = getIcon("/icons/spring-cloud.svg", Icons.class);

}
