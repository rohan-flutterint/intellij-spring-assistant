package com.github.eltonsandre.plugin.idea.spring.assistant.suggestion.service;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public class SuggestionIndexerProjectServiceImpl implements SuggestionIndexerProjectService {

    private static final Logger log = Logger.getInstance(SuggestionIndexerProjectServiceImpl.class);

    private final Project project;

    public SuggestionIndexerProjectServiceImpl(@NotNull final Project project) {
        this.project = project;
    }

    @Override
    public void index() {
        this.index(ModuleManager.getInstance(this.project).getModules());
    }

    @Override
    public void index(final Module[] modules) {
        LogUtil.debug(() -> log.debug("-> Indexing requested for a subset of modules of project " + this.project.getName()));
        Stream.of(modules).forEach(module -> module.getService(SuggestionService.class).index());
    }

}
