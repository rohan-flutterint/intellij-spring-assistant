package com.github.eltonsandre.plugin.spring.filetype;

import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import in.oneton.idea.spring.assistant.plugin.misc.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * https://plugins.jetbrains.com/docs/intellij/language-and-filetype.html
 */
public class PropertiesFileType extends LanguageFileType {

    public static final PropertiesFileType INSTANCE = new PropertiesFileType();

    PropertiesFileType() {
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
    public @Nullable Icon getIcon() {
        return Icons.SpringInitializr;
    }


}
