package com.github.eltonsandre.plugin.idea.spring.assistant.completion.annotation;


import com.github.eltonsandre.plugin.idea.spring.assistant.common.annotation.SpringAnnotationUtil;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import in.oneton.idea.spring.assistant.plugin.misc.GenericUtil;
import in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.completion.FileType;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

import static com.github.eltonsandre.plugin.idea.spring.assistant.common.Constants.PROP_DOT;

public class AnnotationCompletionProvider extends CompletionProvider<CompletionParameters> {

    private static final String REGEX_COMPLETION_SPRING_ANNOTATION_PREFIX = "(\"?(\\$?\\{)?)(.*)(";

    private static final String REGEX_COMPLETION_SPRING_ANNOTATION_SUFFIX = ")(\"?\\)?)(.*)";

    private static final int INDEX_GROUP_KEY_IN_REGEX_SPRING_ANNOTATION = 3;

    private static final Pattern PATTERN_COMPLETION_SPRING_ANNOTATION = Pattern.compile(REGEX_COMPLETION_SPRING_ANNOTATION_PREFIX
            + CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED + REGEX_COMPLETION_SPRING_ANNOTATION_SUFFIX);

    @Override
    protected void addCompletions(@NotNull final CompletionParameters parameters,
                                  @NotNull final ProcessingContext context,
                                  @NotNull CompletionResultSet resultSet) {

        final PsiElement element = parameters.getPosition();
        if (element instanceof PsiComment || SpringAnnotationUtil.isNotAnnotationBySpring(element)) {
            return;
        }

        final var module = PsiCustomUtil.findModule(element);
        if (module == null) {
            return;
        }

        final var service = SuggestionService.getInstance(module);
        if (service.cannotProvideSuggestions()) {
            return;
        }

        final String textOrigin = this.getTextInKeyPositon(element.getText());
        final String textContext = this.getTextInKeyPositon(element.getContext().getText());

        final String queryWithDotDelimitedPrefixes = this.getQueryWithDotDelimitedPrefixes(textOrigin);
        final List<String> ancestralKeys = GenericUtil.getAncestralKey(textContext);
        final List<LookupElement> suggestions = service.findSuggestionsForQueryPrefix(
                FileType.JAVA, element, ancestralKeys, queryWithDotDelimitedPrefixes, null);

        resultSet = resultSet.withPrefixMatcher(queryWithDotDelimitedPrefixes);
        if (suggestions != null) {
            suggestions.forEach(resultSet::addElement);
        }

    }

    private String getTextInKeyPositon(final String text) {
        final var matcher = PATTERN_COMPLETION_SPRING_ANNOTATION.matcher(text);

        if (matcher.matches()) {
            return matcher.group(INDEX_GROUP_KEY_IN_REGEX_SPRING_ANNOTATION);
        }
        return StringUtils.EMPTY;
    }

    private String getQueryWithDotDelimitedPrefixes(final String origin) {
        final int pos = origin.lastIndexOf(PROP_DOT);
        if (pos != -1) {
            return (pos == origin.length() - 1) ? StringUtils.EMPTY : origin.substring(pos + 1);
        }
        return origin;
    }

}
