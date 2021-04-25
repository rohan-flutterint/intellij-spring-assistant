package com.github.eltonsandre.plugin.idea.spring.assistant.filetype;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.Icons;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * https://plugins.jetbrains.com/docs/intellij/language-and-filetype.html
 */
public class ApplicationPropertiesFileType extends LanguageFileType {

    public static final ApplicationPropertiesFileType INSTANCE = new ApplicationPropertiesFileType();

    ApplicationPropertiesFileType() {
        super(PropertiesLanguage.INSTANCE, true);
    }

    @Override
    public @NonNls @NotNull String getName() {
        return "spring-application-config-properties";
    }

    @Override
    public @NotNull String getDescription() {
        return "Spring application config properties file";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "properties";
    }

    @Override
    public @Nls
    @NotNull String getDisplayName() {
        return "Spring application config (properties)";
    }

    @Override
    public @Nullable Icon getIcon() {
        return Icons.springBoot;
    }

}
