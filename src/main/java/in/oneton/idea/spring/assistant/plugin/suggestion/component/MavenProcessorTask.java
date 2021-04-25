package in.oneton.idea.spring.assistant.plugin.suggestion.component;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import static com.github.eltonsandre.plugin.idea.spring.assistant.common.LogUtil.debug;

public class MavenProcessorTask implements MavenProjectsProcessorTask {

    private static final Logger log = Logger.getInstance(MavenProcessorTask.class);

    private final Module module;

    MavenProcessorTask(final Module module) {
        this.module = module;
    }


    @Override
    public void perform(final Project project, final MavenEmbeddersManager mavenEmbeddersManager,
                        final MavenConsole mavenConsole, final MavenProgressIndicator mavenProgressIndicator) {

        debug(() -> log.debug("Project imported successfully, will trigger indexing via dumbservice for project " + project.getName()));

        DumbService.getInstance(project)
                .smartInvokeLater(() -> {
                    log.debug("Will attempt to trigger indexing for project " + project.getName());

                    try {
                        final var service = SuggestionService.getInstance(this.module);

                        if (service.cannotProvideSuggestions()) {
                            service.index();
                        } else {
                            debug(() -> log.debug("Index is already built, no point in rebuilding index for project " + project.getName()));
                        }

                    } catch (final Throwable e) { //NOSONAR
                        log.error("Error occurred while indexing project " + project.getName(), e);
                    }
                });
    }

}
