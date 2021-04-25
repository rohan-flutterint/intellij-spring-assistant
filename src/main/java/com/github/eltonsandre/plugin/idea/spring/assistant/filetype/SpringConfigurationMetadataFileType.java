package com.github.eltonsandre.plugin.idea.spring.assistant.filetype;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.Icons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * https://plugins.jetbrains.com/docs/intellij/language-and-filetype.html
 */
public class SpringConfigurationMetadataFileType extends LanguageFileType {

    public static final SpringConfigurationMetadataFileType INSTANCE = new SpringConfigurationMetadataFileType();

    SpringConfigurationMetadataFileType() {
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

    @Override
    public @Nls
    @NotNull String getDisplayName() {
        return "Spring configuration metadata (json)";
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return Icons.springBoot;
    }

}
