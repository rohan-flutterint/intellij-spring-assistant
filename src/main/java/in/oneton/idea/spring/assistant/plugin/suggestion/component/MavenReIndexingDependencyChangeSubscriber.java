package in.oneton.idea.spring.assistant.plugin.suggestion.component;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.LogUtil;
import com.github.eltonsandre.plugin.idea.spring.assistant.suggestion.service.SuggestionIndexerProjectService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenImportListener;

import java.util.List;
import java.util.Objects;

import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.moduleNamesAsStrCommaDelimited;

public class MavenReIndexingDependencyChangeSubscriber implements ProjectManagerListener {

    private static final Logger log = Logger.getInstance(MavenReIndexingDependencyChangeSubscriber.class);

    private MessageBusConnection connection;

    @Override
    public void projectOpened(@NotNull final Project project) {
        // This will trigger indexing
        final var service = SuggestionIndexerProjectService.getInstance(project);
        LogUtil.debug(() -> log.debug("Subscribing to maven dependency updates for project " + project.getName()));

        try {
            this.connection = project.getMessageBus().connect();
            this.connection.subscribe(MavenImportListener.TOPIC, this.handler(project, service));
        } catch (final Throwable e) { //NOSONAR
            log.error("Failed to subscribe to maven dependency updates for project " + project.getName(), e);
        }
    }

    @NotNull
    private MavenImportListener handler(final @NotNull Project project, final SuggestionIndexerProjectService service) {
        return (importedProjects, newModules) -> {
            final boolean proceed = importedProjects.stream()
                    .filter(proj -> project.getName().equals(proj.getName()))
                    .anyMatch(proj -> proj.getDirectory().equals(project.getBasePath()));

            if (proceed) {
                LogUtil.debug(() -> log.debug("Maven dependencies are updated for project " + project.getName()));
                DumbService.getInstance(project)
                        .smartInvokeLater(() -> this.reindexModules(service, project, newModules));
            }
        };
    }

    private void reindexModules(final SuggestionIndexerProjectService service, @NotNull final Project project, final @NotNull List<Module> newModules) {
        log.debug("Will attempt to trigger indexing for project " + project.getName());
        try {
            final Module[] modules = ModuleManager.getInstance(project).getModules();

            if (modules.length > 0) {
                service.index(modules);
            }
        } catch (final Throwable e) {//NOSONAR
            log.error("Error occurred while indexing project " + project.getName() +
                    " & modules " + moduleNamesAsStrCommaDelimited(newModules, false), e);
        }
    }

    @Override
    public void projectClosed(@NotNull final Project project) {
        if (Objects.nonNull(this.connection)) {
            this.connection.disconnect();
        }
        Disposer.dispose(project);
    }

}
