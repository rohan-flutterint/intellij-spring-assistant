package in.oneton.idea.spring.assistant.plugin.suggestion.component;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.LogUtil;
import com.github.eltonsandre.plugin.idea.spring.assistant.suggestion.service.SuggestionIndexerProjectService;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.compiler.CompilerTopics.COMPILATION_STATUS;

public class Bootstrap implements StartupActivity.DumbAware {

    private static final Logger log = Logger.getInstance(Bootstrap.class);

    @Override
    public void runActivity(@NotNull final Project project) {
        LogUtil.debug(() -> log.debug("Subscribing to compilation events for project " + project.getName()));

        final var indexTask = new Task.Backgroundable(project, "Indexing spring configuration metadata") {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {

                DumbService.getInstance(project).runReadActionInSmartMode(() -> {
                    LogUtil.debug(() -> log.debug("Project " + project.getName() + " is opened, indexing will start"));

                    try {
                        SuggestionIndexerProjectService.getInstance(project).index();
                    } finally {
                        LogUtil.debug(() -> log.debug("Indexing complete for project " + project.getName()));
                    }
                });
            }
        };

        indexTask.setCancelText("Stop Loading").queue();
        ProgressManager.getInstance().run(indexTask);

        final var compilationStatusListener = new CompilationStatusListener() {
            @Override
            public void compilationFinished(final boolean aborted, final int errors, final int warnings,
                                            @NotNull final CompileContext compileContext) {
                LogUtil.debug(() -> log.debug("Received compilation status event for project " + project.getName()));

                if (errors == 0) {
                    final var compileScope = compileContext.getCompileScope();
                    var service = SuggestionIndexerProjectService.getInstance(project);

                    if (compileContext.getProjectCompileScope() == compileScope) {
                        service.index();
                    } else {
                        service.index(compileScope.getAffectedModules());
                    }
                    LogUtil.debug(() -> log.debug("Compilation status processed for project " + project.getName()));
                }
            }
        };

        try {
            project.getMessageBus().connect().subscribe(COMPILATION_STATUS, compilationStatusListener);

            LogUtil.debug(() -> log.debug("Subscribe to compilation events for project " + project.getName()));
        } catch (final Throwable e) { //NOSONAR
            log.error("Failed to subscribe to compilation events for project " + project.getName(), e);
        }
    }

}
