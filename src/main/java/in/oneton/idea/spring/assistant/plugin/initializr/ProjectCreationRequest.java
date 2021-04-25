package in.oneton.idea.spring.assistant.plugin.initializr;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import in.oneton.idea.spring.assistant.plugin.initializr.metadata.InitializerMetadata;
import in.oneton.idea.spring.assistant.plugin.initializr.metadata.InitializerMetadata.DependencyComposite.DependencyGroup.Dependency;
import in.oneton.idea.spring.assistant.plugin.initializr.metadata.InitializerMetadata.IdAndName;
import in.oneton.idea.spring.assistant.plugin.initializr.metadata.InitializerMetadata.IdContainer;
import in.oneton.idea.spring.assistant.plugin.initializr.metadata.InitializerMetadata.ProjectTypeComposite.ProjectType;
import in.oneton.idea.spring.assistant.plugin.initializr.metadata.io.spring.initializr.util.Version;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.github.eltonsandre.plugin.idea.spring.assistant.common.Constants.AMPERSAND;
import static com.intellij.openapi.projectRoots.JavaSdkVersion.fromLanguageLevel;
import static com.intellij.openapi.util.io.FileUtil.sanitizeFileName;
import static com.intellij.pom.java.LanguageLevel.parse;
import static com.intellij.psi.impl.PsiNameHelperImpl.getInstance;
import static in.oneton.idea.spring.assistant.plugin.initializr.misc.InitializrUtil.from;
import static in.oneton.idea.spring.assistant.plugin.initializr.misc.InitializrUtil.nameAndValueAsUrlParam;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Data
public class ProjectCreationRequest {

    private String serverUrl;
    private InitializerMetadata metadata;

    private ProjectType type;
    private String groupId;
    private String artifactId;
    private String version;
    private String name;
    private String description;
    private String packageName;
    private IdAndName language;
    private IdAndName javaVersion;
    private IdAndName packaging;

    private Version bootVersion;
    private LinkedHashSet<Dependency> dependencies = new LinkedHashSet<>();

    private static String sanitize(final String input) {
        return sanitizeFileName(input, false).replace(' ', '-').toLowerCase();
    }

    public void setServerUrl(final String serverUrl) {
        if (!serverUrl.equals(this.serverUrl)) {
            this.serverUrl = serverUrl;
            this.metadata = null;
        }
    }

    public boolean isJavaVersionSet() {
        return this.javaVersion != null;
    }

    @Nullable
    public Dependency getDependencyAtIndex(int index) {
        final Iterator<Dependency> iterator = this.dependencies.iterator();
        Dependency dependency = null;
        while (index >= 0) {
            if (iterator.hasNext()) {
                dependency = iterator.next();
            } else {
                dependency = null;
                break;
            }
            index--;
        }
        return dependency;
    }

    public int getIndexOfDependency(@NotNull final Dependency dependency) {
        final Iterator<Dependency> iterator = this.dependencies.iterator();
        int dependencyIndex = -1;
        int index = 0;
        while (iterator.hasNext()) {
            if (iterator.next().equals(dependency)) {
                dependencyIndex = index;
                break;
            }
            index++;
        }
        return dependencyIndex;
    }

    public boolean addDependency(final Dependency dependency) {
        return this.dependencies.add(dependency);
    }

    public boolean removeDependency(final Dependency dependency) {
        return this.dependencies.removeIf(v -> v.equals(dependency));
    }

    public void removeIncompatibleDependencies(final Version newVersion) {
        this.dependencies.removeIf(dependency -> !dependency.isVersionCompatible(newVersion));
    }

    public int getDependencyCount() {
        return this.dependencies.size();
    }

    public boolean containsDependency(final Dependency dependency) {
        return this.getIndexOfDependency(dependency) != -1;
    }

    public boolean hasValidGroupId() {
        return !isEmpty(this.groupId);
    }

    public boolean hasValidArtifactId() {
        return !isEmpty(this.artifactId) && sanitize(this.artifactId).equals(this.artifactId);
    }

    public boolean hasValidVersion() {
        return !isEmpty(this.version);
    }

    public boolean hasValidName() {
        return !isEmpty(this.name);
    }

    public boolean hasCompatibleJavaVersion(final ModuleBuilder moduleBuilder,
                                            final WizardContext wizardContext) {
        final JavaSdkVersion wizardSdkVersion = from(wizardContext, moduleBuilder);
        if (wizardSdkVersion != null) {
            final LanguageLevel selectedLanguageLevel = parse(this.javaVersion.getId());
            final JavaSdkVersion selectedSdkVersion =
                    selectedLanguageLevel != null ? fromLanguageLevel(selectedLanguageLevel) : null;
            // only if selected java version is compatible with wizard version
            return selectedSdkVersion == null || wizardSdkVersion.isAtLeast(selectedSdkVersion);
        }
        return true;
    }

    public boolean hasValidPackageName() {
        return !isEmpty(this.packageName) && getInstance().isQualifiedName(this.packageName);
    }

    public boolean isServerUrlSet() {
        return !isEmpty(this.serverUrl);
    }

    public String buildDownloadUrl() {//@formatter:off
        return this.serverUrl + this.type.getAction() + "?" +
                nameAndValueAsUrlParam( "type", this.type.getId()) + AMPERSAND +
                nameAndValueAsUrlParam("groupId", this.groupId) + AMPERSAND +
                nameAndValueAsUrlParam("artifactId", this.artifactId) + AMPERSAND +
                nameAndValueAsUrlParam("version", this.version) + AMPERSAND +
                nameAndValueAsUrlParam("name", this.name) + AMPERSAND +
                nameAndValueAsUrlParam("description", this.description) + AMPERSAND +
                nameAndValueAsUrlParam("packageName", this.packageName) + AMPERSAND +
                nameAndValueAsUrlParam("language", this.language.getId()) + AMPERSAND +
                nameAndValueAsUrlParam("javaVersion", this.javaVersion.getId()) + AMPERSAND +
                nameAndValueAsUrlParam("packaging", this.packaging.getId()) + AMPERSAND +
                nameAndValueAsUrlParam("bootVersion", this.bootVersion.toString()) + AMPERSAND +
                this.dependencies.stream()
                        .map(Dependency::getId)
                        .map(dependencyId -> nameAndValueAsUrlParam("dependencies", dependencyId))
                        .collect(joining(AMPERSAND));
    }//@formatter:on

    public <T> T getSetProperty(@NotNull final Consumer<T> setter, @NotNull final Supplier<T> getter,
                                @Nullable final T defaultValue) {
        if (getter.get() == null) {
            setter.accept(defaultValue);
        }
        return getter.get();
    }

    @Nullable
    public <T extends IdContainer> T getSetIdContainer(@NotNull final Consumer<T> setter,
                                                       @NotNull final Supplier<T> getter, @NotNull final Collection<T> containers, @Nullable final String defaultId) {
        if (getter.get() == null && defaultId != null) {
            containers.stream().filter(packagingType -> packagingType.getId().equals(defaultId))
                    .findFirst().ifPresent(setter);
        }
        return getter.get();
    }

    @Nullable
    public Version getSetVersion(@NotNull final Collection<IdAndName> containers,
                                 @Nullable final String defaultVersionId) {
        if (this.bootVersion == null && defaultVersionId != null) {
            final Version defaultVersion = Version.parse(defaultVersionId);
            containers.stream().filter(idAndName -> idAndName.parseIdAsVersion().equals(defaultVersion))
                    .findFirst().ifPresent(v -> this.bootVersion = v.parseIdAsVersion());
        }
        return this.bootVersion;
    }

}
