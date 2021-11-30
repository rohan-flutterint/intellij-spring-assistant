package in.oneton.idea.spring.assistant.plugin.suggestion.component;

import com.intellij.openapi.project.Project;

public interface MavenReIndexingDependencyChangeSubscriber {

  static MavenReIndexingDependencyChangeSubscriber getInstance(Project project) {
    return project.getService(MavenReIndexingDependencyChangeSubscriber.class);
  }

}
