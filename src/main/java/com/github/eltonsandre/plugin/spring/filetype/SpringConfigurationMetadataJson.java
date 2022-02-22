package com.github.eltonsandre.plugin.spring.filetype;

import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import in.oneton.idea.spring.assistant.plugin.misc.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * https://plugins.jetbrains.com/docs/intellij/language-and-filetype.html
 */
public class SpringConfigurationMetadataJson extends LanguageFileType {

    public static final SpringConfigurationMetadataJson INSTANCE = new SpringConfigurationMetadataJson();

    SpringConfigurationMetadataJson() {
        super(JsonLanguage.INSTANCE, true);
    }

    @NotNull
    @Override
    public String getName() {
        return "spring-configuration-metadata";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Spring configuration metadata file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "json";
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return Icons.SpringInitializr;
    }

}
