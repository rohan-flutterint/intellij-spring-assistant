package in.oneton.idea.spring.assistant.plugin.suggestion.handler;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.OriginalNameProvider;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.editor.EditorModificationUtil.insertStringAtCaret;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getCodeStyleIntent;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getIndent;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getOverallIndent;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.CARET;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.UNDEFINED;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.UNKNOWN_CLASS;

public class YamlKeyInsertHandler implements InsertHandler<LookupElement> {

    @Override
    public void handleInsert(final @NotNull InsertionContext context, final @NotNull LookupElement lookupElement) {
        if (!this.nextCharAfterSpacesAndQuotesIsColon(this.getStringAfterAutoCompletedValue(context))) {
            final String existingIndentation = this.getExistingIndentation(context, lookupElement);
            final Suggestion suggestion = (Suggestion) lookupElement.getObject();
            final String indentPerLevel = getCodeStyleIntent(context);
            final Module module = findModule(context);

            final String suggestionWithCaret = this.getSuggestionReplacementWithCaret(module, suggestion, existingIndentation, indentPerLevel);
            final String suggestionWithoutCaret = suggestionWithCaret.replace(CARET, StringUtils.EMPTY);

            final PsiElement currentElement = context.getFile().findElementAt(context.getStartOffset());

            if (Objects.isNull(currentElement)) {
                throw new AssertionError("no element at " + context.getStartOffset());
            }

            this.deleteLookupTextAndRetrieveOldValue(context, currentElement);

            insertStringAtCaret(context.getEditor(), suggestionWithoutCaret, false, true,
                    this.getCaretIndex(suggestionWithCaret));
        }
    }

    private int getCaretIndex(final String suggestionWithCaret) {
        return suggestionWithCaret.indexOf(CARET);
    }

    private String getExistingIndentation(final InsertionContext context, final LookupElement item) {
        final String stringBeforeAutoCompletedValue = this.getStringBeforeAutoCompletedValue(context, item);
        return this.getExistingIndentationInRowStartingFromEnd(stringBeforeAutoCompletedValue);
    }

    @NotNull
    private String getStringAfterAutoCompletedValue(final InsertionContext context) {
        return context.getDocument().getText().substring(context.getTailOffset());
    }

    @NotNull
    private String getStringBeforeAutoCompletedValue(final InsertionContext context, final LookupElement item) {
        return context.getDocument().getText()
                .substring(0, context.getTailOffset() - item.getLookupString().length());
    }

    private boolean nextCharAfterSpacesAndQuotesIsColon(final String string) {
        for (int i = 0; i < string.length(); i++) {
            final char c = string.charAt(i);
            if (c != ' ' && c != '"') {
                return c == ':';
            }
        }
        return false;
    }

    private String getExistingIndentationInRowStartingFromEnd(final String val) {
        int count = 0;
        for (int i = val.length() - 1; i >= 0; i--) {
            final char c = val.charAt(i);
            if (c != '\t' && c != ' ' && c != '-') {
                break;
            }
            count++;
        }
        return val.substring(val.length() - count).replace("-", StringUtils.SPACE);
    }

    private void deleteLookupTextAndRetrieveOldValue(final InsertionContext context, @NotNull final PsiElement elementAtCaret) {
        if (elementAtCaret.getNode().getElementType() != YAMLTokenTypes.SCALAR_KEY) {
            this.deleteLookupPlain(context);
        } else {
            final YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(elementAtCaret, YAMLKeyValue.class);
            assert keyValue != null;
            context.commitDocument();

            // TODO: Whats going on here?
            if (keyValue.getValue() != null) {
                final YAMLKeyValue dummyKV =
                        YAMLElementGenerator.getInstance(context.getProject()).createYamlKeyValue("foo", "b");
                dummyKV.setValue(keyValue.getValue());
            }

            context.setTailOffset(keyValue.getTextRange().getEndOffset());
            runWriteCommandAction(context.getProject(),
                    () -> keyValue.getParentMapping().deleteKeyValue(keyValue));
        }
    }

    private void deleteLookupPlain(final InsertionContext context) {
        final Document document = context.getDocument();
        document.deleteString(context.getStartOffset(), context.getTailOffset());
        context.commitDocument();
    }

    @NotNull
    private String getSuggestionReplacementWithCaret(final Module module, final Suggestion suggestion,
                                                     final String existingIndentation, final String indentPerLevel) {
        final List<? extends OriginalNameProvider> matchesTopFirst = suggestion.getMatchesForReplacement();

        final StringBuilder builder = new StringBuilder();
        final AtomicInteger count = new AtomicInteger(0);
        matchesTopFirst.forEach(nameProvider ->
                builder.append(StringUtils.LF)
                        .append(existingIndentation)
                        .append(getIndent(indentPerLevel, count.getAndIncrement()))
                        .append(nameProvider.getOriginalName())
                        .append(":")
        );

        builder.delete(0, existingIndentation.length() + 1);

        final String indentForNextLevel = getOverallIndent(existingIndentation, indentPerLevel, matchesTopFirst.size());
        final String sufix = this.getPlaceholderSufixWithCaret(module, suggestion, indentForNextLevel);

        builder.append(sufix);

        return builder.toString();
    }

    @NotNull
    private String getPlaceholderSufixWithCaret(final Module module, final Suggestion suggestion, final String indentForNextLevel) {

        if (suggestion.getLastSuggestionNode().isMetadataNonProperty()) {
            return StringUtils.LF + indentForNextLevel + CARET;
        }

        final SuggestionNodeType nodeType = suggestion.getSuggestionNodeType(module);
        if (nodeType == UNDEFINED || nodeType == UNKNOWN_CLASS) {
            return CARET;

        } else if (nodeType.representsLeaf()) {
            return StringUtils.SPACE + CARET;

        } else if (nodeType.representsArrayOrCollection()) {
            return StringUtils.LF + indentForNextLevel + "- " + CARET;

        } else { // map or class
            return StringUtils.LF + indentForNextLevel + CARET;
        }
    }

}
