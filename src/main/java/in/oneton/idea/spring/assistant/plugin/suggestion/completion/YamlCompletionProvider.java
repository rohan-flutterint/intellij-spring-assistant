package in.oneton.idea.spring.assistant.plugin.suggestion.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.truncateIdeaDummyIdentifier;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode.sanitise;
import static java.util.Objects.requireNonNull;

class YamlCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull final CompletionParameters completionParameters,
                                  final @NotNull ProcessingContext processingContext,
                                  @NotNull final CompletionResultSet resultSet) {

        final PsiElement element = completionParameters.getPosition();
        if (element instanceof PsiComment) {
            return;
        }

        final Module module = findModule(element);
        if (module == null) {
            return;
        }

        final var service = SuggestionService.getInstance(module);
        if (service.cannotProvideSuggestions()) {
            return;
        }

        final PsiElement elementContext = element.getContext();
        final PsiElement parent = requireNonNull(elementContext).getParent();
        if (parent instanceof YAMLSequence) {
            // lets force user to create array element prefix before he can ask for suggestions
            return;
        }

        List<String> ancestralKeys = null;
        PsiElement context = elementContext;
        do {
            if (context instanceof YAMLKeyValue) {
                if (ancestralKeys == null) {
                    ancestralKeys = new ArrayList<>();
                }
                ancestralKeys.add(0, truncateIdeaDummyIdentifier(((YAMLKeyValue) context).getKeyText()));
            }
            context = requireNonNull(context).getParent();
        } while (context != null);

        // For top level element, since there is no parent parentKeyValue would be null
        final String queryWithDotDelimitedPrefixes = truncateIdeaDummyIdentifier(element);
        final Set<String> siblingsToExclude = this.getSiblingsToExclude(elementContext, parent);

        final List<LookupElement> suggestions = service
                .findSuggestionsForQueryPrefix(FileType.YAML, element, ancestralKeys, queryWithDotDelimitedPrefixes, siblingsToExclude);

        if (suggestions != null) {
            suggestions.forEach(resultSet::addElement);
        }
    }

    @Nullable
    private Set<String> getSiblingsToExclude(final PsiElement elementContext, final PsiElement parent) {
        Set<String> siblingsToExclude = null;
        if (parent instanceof YAMLSequenceItem) {

            for (final PsiElement child : parent.getParent().getChildren()) {
                if (child != parent) {
                    if (child instanceof YAMLSequenceItem) {
                        final YAMLValue value = ((YAMLSequenceItem) child).getValue();
                        if (value != null) {
                            siblingsToExclude = this.addSanitiseValue(siblingsToExclude, value.getText());
                        }

                    } else if (child instanceof YAMLKeyValue) {
                        siblingsToExclude = this.addSanitiseValue(siblingsToExclude, ((YAMLKeyValue) child).getKeyText());
                    }
                }
            }

        } else if (parent instanceof YAMLMapping) {
            for (final PsiElement child : parent.getChildren()) {
                if (child != elementContext && child instanceof YAMLKeyValue) {
                    siblingsToExclude = this.addSanitiseValue(siblingsToExclude, ((YAMLKeyValue) child).getKeyText());
                }
            }
        }
        return siblingsToExclude;
    }

    @NotNull
    private Set<String> addSanitiseValue(final Set<String> siblingsToExclude, final @NlsSafe String value) {
        if (siblingsToExclude == null) {
            return new THashSet<>();
        }
        siblingsToExclude.add(sanitise(value));
        return siblingsToExclude;
    }

}
