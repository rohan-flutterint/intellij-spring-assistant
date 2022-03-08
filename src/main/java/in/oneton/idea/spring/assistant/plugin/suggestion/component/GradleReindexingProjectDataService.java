package in.oneton.idea.spring.assistant.plugin.suggestion.component;

import com.github.eltonsandre.plugin.idea.spring.assistant.suggestion.service.SuggestionIndexerProjectService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.github.eltonsandre.plugin.idea.spring.assistant.common.LogUtil.debug;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExternalRootProjectPath;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.moduleNamesAsStrCommaDelimited;
import static java.util.Arrays.stream;

/**
 * Callback that gets invoked by gradle as soon as the project is imported successfully
 */
@Order(5000)
public class GradleReindexingProjectDataService extends AbstractProjectDataService<ModuleData, Void> {

    private static final Logger log = Logger.getInstance(GradleReindexingProjectDataService.class);

    @NotNull
    @Override
    public Key<ModuleData> getTargetDataKey() {
        return ProjectKeys.MODULE;
    }

    @Override
    public void onSuccessImport(@NotNull final Collection<DataNode<ModuleData>> imported,
                                @Nullable final ProjectData projectData, @NotNull final Project project,
                                @NotNull final IdeModelsProvider modelsProvider) {

        if (projectData != null) {
            debug(() -> log.debug("Gradle dependencies are updated for project, will trigger indexing via dumbservice for project " + project.getName()));

            DumbService.getInstance(project).smartInvokeLater(() -> {
                log.debug("Will attempt to trigger indexing for project " + project.getName());
                final var service = SuggestionIndexerProjectService.getInstance(project);

                try {
                    final Module[] validModules = stream(modelsProvider.getModules())
                            .filter(module -> projectData.getLinkedExternalProjectPath().equals(getExternalRootProjectPath(module)))
                            .toArray(Module[]::new);

                    if (validModules.length > 0) {
                        service.index(validModules);
                    } else {
                        debug(() -> log.debug("None of the modules " + moduleNamesAsStrCommaDelimited(modelsProvider.getModules(), Boolean.TRUE) +
                                " are relevant for indexing, skipping for project " + project.getName()));
                    }
                } catch (final Throwable e) {
                    log.error("Error occurred while indexing project " + project.getName() + " & modules " +
                            moduleNamesAsStrCommaDelimited(modelsProvider.getModules(), Boolean.FALSE), e);
                }
            });
        }
    }

}
