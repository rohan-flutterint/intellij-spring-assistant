package in.oneton.idea.spring.assistant.plugin.suggestion.service;

import com.intellij.openapi.module.Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

// TODO: Fix this
@ExtendWith(MockitoExtension.class)
class SuggestionServiceImplTest {

    @Mock
    Module mockedModule;
    SuggestionServiceImpl suggestionIndexService;

    @BeforeEach
    void setUp() {
        this.suggestionIndexService = new SuggestionServiceImpl(this.mockedModule);
    }

    @Test
    void canProvideSuggestions() {
    }

    @Test
    void getSuggestions() {
    }

    @Test
    void buildMetadataHierarchy() {
    }

    @Test
    void findDeepestMatch() {
    }

    @Test
    void giveFindSuggestionSource_whenResourceIsPresentInClasspath_thenFindSources() {
        //    mockedModule.
    }

    class SuggestionServiceImpl extends in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionServiceImpl {

        SuggestionServiceImpl(final Module module) {
            super(module);
        }
    }

}
