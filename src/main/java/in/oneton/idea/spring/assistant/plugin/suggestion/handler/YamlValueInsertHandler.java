package in.oneton.idea.spring.assistant.plugin.suggestion.handler;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.text.CharArrayUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

// a large section of this class is borrowed from https://github.com/zalando/intellij-swagger
public class YamlValueInsertHandler implements InsertHandler<LookupElement> {

    private static final char SINGLE_QUOTE = '\'';
    private static final char DOUBLE_QUOTE = '"';

    private static final char[] RESERVED_YAML_CHARS = {':', '{', '}', '[', ']', ',', '&', '*', '#', '?', '|', '-', '<', '>', '=', '!', '%', '@', '`'};

    public static String unescapeValue(final String value) {
        return value.replaceAll("^['\"]", StringUtils.EMPTY)
                .replaceAll("['\"]$", StringUtils.EMPTY);
    }

    @Override
    public void handleInsert(final @NotNull InsertionContext insertionContext, final @NotNull LookupElement lookupElement) {
        if (this.shouldUseQuotes(lookupElement)) {
            final boolean hasDoubleQuotes = this.hasStartingOrEndingQuoteOfType(insertionContext, lookupElement);

            final char quoteType = hasDoubleQuotes ? DOUBLE_QUOTE : SINGLE_QUOTE;

            this.handleEndingQuote(insertionContext, quoteType);
            this.handleStartingQuote(insertionContext, lookupElement, quoteType);
        }
    }

    private boolean shouldUseQuotes(final LookupElement lookupElement) {
        return StringUtils.containsAny(lookupElement.getLookupString(), RESERVED_YAML_CHARS);
    }

    private boolean hasStartingOrEndingQuoteOfType(final InsertionContext insertionContext, final LookupElement lookupElement) {
        final int caretOffset = insertionContext.getEditor().getCaretModel().getOffset();
        final int startOfLookupStringOffset = caretOffset - lookupElement.getLookupString().length();

        final boolean hasStartingQuote = this.hasStartingQuote(insertionContext, DOUBLE_QUOTE, startOfLookupStringOffset);
        final boolean hasEndingQuote = this.hasEndingQuote(insertionContext, caretOffset, DOUBLE_QUOTE);

        return hasStartingQuote || hasEndingQuote;
    }

    private boolean hasEndingQuote(final InsertionContext insertionContext, final int caretOffset, final char quoteType) {
        final CharSequence chars = insertionContext.getDocument().getCharsSequence();

        return CharArrayUtil.regionMatches(chars, caretOffset, String.valueOf(quoteType));
    }

    private boolean hasStartingQuote(final InsertionContext insertionContext, final char quoteType, final int startOfLookupStringOffset) {
        return insertionContext.getDocument().getText().charAt(startOfLookupStringOffset - 1) == quoteType;
    }

    private void handleStartingQuote(final InsertionContext insertionContext, final LookupElement lookupElement, final char quoteType) {
        final int caretOffset = insertionContext.getEditor().getCaretModel().getOffset();
        final int startOfLookupStringOffset = caretOffset - lookupElement.getLookupString().length();

        final boolean hasStartingQuote = this.hasStartingQuote(insertionContext, quoteType, startOfLookupStringOffset);
        if (!hasStartingQuote) {
            insertionContext.getDocument()
                    .insertString(startOfLookupStringOffset, String.valueOf(quoteType));
        }
    }

    private void handleEndingQuote(final InsertionContext insertionContext, final char quoteType) {
        final int caretOffset = insertionContext.getEditor().getCaretModel().getOffset();

        final boolean hasEndingQuote = this.hasEndingQuote(insertionContext, caretOffset, quoteType);
        if (!hasEndingQuote) {
            insertionContext.getDocument()
                    .insertString(caretOffset, String.valueOf(quoteType));
        }
    }

}
