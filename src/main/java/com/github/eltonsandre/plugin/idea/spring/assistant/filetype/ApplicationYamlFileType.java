package com.github.eltonsandre.plugin.idea.spring.assistant.filetype;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.Icons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;

import javax.swing.*;

/**
 * https://plugins.jetbrains.com/docs/intellij/language-and-filetype.html
 */
public class ApplicationYamlFileType extends LanguageFileType {

    public static final ApplicationYamlFileType INSTANCE = new ApplicationYamlFileType();

    ApplicationYamlFileType() {
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

    @Override
    public @Nls
    @NotNull String getDisplayName() {
        return "Spring application config (yaml)";
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return Icons.springBoot;
    }

}
