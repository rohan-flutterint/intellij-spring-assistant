package com.github.eltonsandre.plugin.idea.spring.assistant.filetype;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.Icons;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class BootstrapPropertiesFileType extends LanguageFileType {

    public static final BootstrapPropertiesFileType INSTANCE = new BootstrapPropertiesFileType();

    BootstrapPropertiesFileType() {
        super(PropertiesLanguage.INSTANCE, true);
    }

    @NotNull
    @Override
    public String getName() {
        return "spring-cloud-config-properties";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Spring cloud config properties file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "properties";
    }

    @Override
    public @Nls
    @NotNull String getDisplayName() {
        return "Spring Bootstrap (properties)";
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return Icons.springCloud;
    }

}
