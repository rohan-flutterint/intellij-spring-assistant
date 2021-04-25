package com.github.eltonsandre.plugin.idea.spring.assistant.suggestion.service;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface SuggestionIndexerProjectService {

    static SuggestionIndexerProjectService getInstance(@NotNull final Project project) {
        return project.getService(SuggestionIndexerProjectService.class);
    }

    void index();

    void index(Module[] modules);

}
