package in.oneton.idea.spring.assistant.plugin.suggestion.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import in.oneton.idea.spring.assistant.plugin.misc.GenericUtil;
import in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static com.github.eltonsandre.plugin.idea.spring.assistant.common.Constants.PROP_DOT;

class PropertiesCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull final CompletionParameters completionParameters,
                                  final @NotNull ProcessingContext processingContext, @NotNull CompletionResultSet resultSet) {

        final PsiElement element = completionParameters.getPosition();
        if (element instanceof PsiComment) {
            return;
        }

        final boolean middle = element.getText().endsWith(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
        if (!middle) {
            return;
        }

        final Module module = PsiCustomUtil.findModule(element);
        final var project = element.getProject();
        final SuggestionService service = project.getService(SuggestionService.class);
        if (module == null || !service.canProvideSuggestions(module)) {
            return;
        }

        final PsiElement elementContext = element.getContext();

        final String text = GenericUtil.truncateIdeaDummyIdentifier(Objects.requireNonNull(elementContext).getText());

        final List<LookupElementBuilder> suggestions;
        // For top level element, since there is no parent keyValue would be null
        final String origin = GenericUtil.truncateIdeaDummyIdentifier(element);

        final int pos = origin.lastIndexOf(PROP_DOT);

        String queryWithDotDelimitedPrefixes = origin;

        if (pos != -1) {
            if (pos == origin.length() - 1) {
                queryWithDotDelimitedPrefixes = StringUtils.EMPTY;
            } else {
                queryWithDotDelimitedPrefixes = origin.substring(pos + 1);
            }
        }

        final List<String> ancestralKeys = GenericUtil.getAncestralKey(text);

        suggestions = service.findSuggestionsForQueryPrefix(module, FileType.PROPERTIES, element,
                ancestralKeys, queryWithDotDelimitedPrefixes, null);

        resultSet = resultSet.withPrefixMatcher(queryWithDotDelimitedPrefixes);

        if (suggestions != null) {
            suggestions.forEach(resultSet::addElement);
        }
    }

}
