package com.github.eltonsandre.plugin.spring.filetype;

import com.intellij.openapi.fileTypes.LanguageFileType;
import in.oneton.idea.spring.assistant.plugin.misc.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;

import javax.swing.*;

/**
 * https://plugins.jetbrains.com/docs/intellij/language-and-filetype.html
 */
public class YamlFileType extends LanguageFileType {

    public static final YamlFileType INSTANCE = new YamlFileType();

    YamlFileType() {
        super(YAMLLanguage.INSTANCE, true);
    }

    @NotNull
    @Override
    public String getName() {
        return "spring-application-config-yaml";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Spring application config yaml file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "yaml";
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return Icons.SpringInitializr;
    }

}
