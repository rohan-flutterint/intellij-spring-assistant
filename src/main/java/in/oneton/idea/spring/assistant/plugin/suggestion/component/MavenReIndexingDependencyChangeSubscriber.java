package in.oneton.idea.spring.assistant.plugin.suggestion.component;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.LogUtil;
import com.github.eltonsandre.plugin.idea.spring.assistant.suggestion.service.SuggestionIndexerProjectService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenImportListener;

import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.moduleNamesAsStrCommaDelimited;

public class MavenReIndexingDependencyChangeSubscriber implements StartupActivity {

    private static final Logger log = Logger.getInstance(MavenReIndexingDependencyChangeSubscriber.class);

    @Override
    public void runActivity(@NotNull final Project project) {
        LogUtil.debug(() -> log.debug("Subscribing to maven dependency updates for project " + project.getName()));

        MavenImportListener mavenImportListener = (importedProjects, newModules) -> {

            final boolean proceed = importedProjects.stream()
                    .filter(p -> project.getName().equals(p.getDisplayName()))
                    .anyMatch(p -> p.getDirectory().equals(project.getBasePath()));

            if (proceed) {
                LogUtil.debug(() -> log.debug("Maven dependencies are updated for project " + project.getName()));

                DumbService.getInstance(project).smartInvokeLater(() -> {
                    log.debug("Will attempt to trigger indexing for project " + project.getName());

                    try {
                        final Module[] modules = ModuleManager.getInstance(project).getModules();
                        if (modules.length > 0) {
                            SuggestionIndexerProjectService.getInstance(project).index(modules);
                            return;
                        }

                        LogUtil.debug(() -> log.debug("Skipping indexing for project " + project.getName() + " as there are no modules"));
                    } catch (final Throwable e) {
                        log.error("Error occurred while indexing project " + project.getName() + " & modules " +
                                moduleNamesAsStrCommaDelimited(newModules, false), e);
                    }
                });

            } else {
                log.debug("Skipping indexing as none of the imported projects match our project " + project.getName());
            }
        };

        try {
            project.getMessageBus().connect().subscribe(MavenImportListener.TOPIC, mavenImportListener);
        } catch (final Throwable e) {
            log.error("Failed to subscribe to maven dependency updates for project " + project.getName(), e);
        }
    }

}
