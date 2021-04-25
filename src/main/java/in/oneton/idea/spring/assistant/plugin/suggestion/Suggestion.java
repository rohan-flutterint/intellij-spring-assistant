package in.oneton.idea.spring.assistant.plugin.suggestion;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.Constants;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import in.oneton.idea.spring.assistant.plugin.suggestion.clazz.ClassMetadata;
import in.oneton.idea.spring.assistant.plugin.suggestion.completion.FileType;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataDeprecationLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis;
import static com.intellij.ui.JBColor.RED;
import static com.intellij.ui.JBColor.YELLOW;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.dotDelimitedOriginalNames;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.getFirstSentenceWithoutDot;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.jetbrains.yaml.YAMLHighlighter.SCALAR_TEXT;

@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Suggestion implements Comparable<Suggestion> {

    public static final String PERIOD_DELIMITER = "\\.";

    private static final LookupElementRenderer<LookupElement> CUSTOM_SUGGESTION_RENDERER = new LookupElementRenderer<>() {
        @Override
        public void renderElement(final LookupElement element, final LookupElementPresentation presentation) {
            final Suggestion suggestion = (Suggestion) element.getObject();
            if (suggestion.icon != null) {
                presentation.setIcon(suggestion.icon);
            }

            presentation.setStrikeout(suggestion.deprecationLevel != null);
            if (suggestion.deprecationLevel != null) {
                if (suggestion.deprecationLevel == SpringConfigurationMetadataDeprecationLevel.error) {
                    presentation.setItemTextForeground(RED);
                } else {
                    presentation.setItemTextForeground(YELLOW);
                }
            }

            final String lookupString = element.getLookupString();
            presentation.setItemText(lookupString);
            if (!lookupString.equals(suggestion.suggestionToDisplay)) {
                presentation.setItemTextBold(true);
            }

            final String shortDescription;
            if (suggestion.defaultValue != null) {
                shortDescription = shortenTextWithEllipsis(suggestion.defaultValue, 60, 0, true);
                final TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(SCALAR_TEXT);
                presentation.setTailText(Constants.EQUALS_SIGN + shortDescription, attrs.getForegroundColor());
            }

            if (suggestion.description != null) {
                presentation.appendTailText(" (" + getFirstSentenceWithoutDot(suggestion.description) + ")", true);
            }

            if (suggestion.shortType != null) {
                presentation.setTypeText(suggestion.shortType);
            }
        }
    };

    @NotNull
    @EqualsAndHashCode.Include
    private final String suggestionToDisplay;

    @Nullable
    private final String description;

    @Nullable
    private final String shortType;

    @Nullable
    private final String defaultValue;

    @Nullable
    private final SpringConfigurationMetadataDeprecationLevel deprecationLevel;
    /**
     * There are two approaches to storing a reference to suggestion
     * <ol>
     * <li>Storing the whole value (support dynamic nodes aswell, as a single PsiClass as leaf might be referred via multiple paths)</li>
     * <li>Storing reference to leaf & navigate up till the root (efficient)</li>
     * </ol>
     * The second solution does not address suggestions that are derived from {@link ClassMetadata} as these nodes are not tied to a single branch of the suggestion tree
     */
    @NotNull
    private final List<? extends SuggestionNode> matchesTopFirst;
    /**
     * Defines the number of ancestors from root, below which the current suggestion should be shown
     */
    private final int numOfAncestors;
    /**
     * Whether or not the suggestion corresponds to value within key -> value pair
     */
    private final boolean forValue;
    /**
     * Whether the current value represents the default value
     */
    @Setter
    private boolean representingDefaultValue;
    /**
     * Type of file that requested this suggestion
     */
    @NotNull
    private final FileType fileType;
    @Nullable
    private final Icon icon;

    private final String pathDotDelimitedRootToLeaf;

    @Builder
    public Suggestion(@NotNull final String suggestionToDisplay, @Nullable final String description,
                      @Nullable final String shortType, @Nullable final String defaultValue,
                      @Nullable final SpringConfigurationMetadataDeprecationLevel deprecationLevel,
                      @NotNull final List<? extends SuggestionNode> matchesTopFirst, final int numOfAncestors, final boolean forValue,
                      final boolean representingDefaultValue, @NotNull final FileType fileType, @Nullable final Icon icon) {
        this.suggestionToDisplay = suggestionToDisplay;
        this.description = description;
        this.shortType = shortType;
        this.defaultValue = defaultValue;
        this.deprecationLevel = deprecationLevel;
        this.matchesTopFirst = matchesTopFirst;
        this.numOfAncestors = numOfAncestors;
        this.forValue = forValue;
        this.representingDefaultValue = representingDefaultValue;
        this.fileType = fileType;
        this.icon = icon;
        this.pathDotDelimitedRootToLeaf =
                matchesTopFirst.stream().map(SuggestionNode::getOriginalName).collect(joining("."));
    }

    public LookupElementBuilder newLookupElement() {
        LookupElementBuilder builder = LookupElementBuilder.create(this, this.suggestionToDisplay);
        if (this.forValue) {
            if (this.description != null) {
                builder = builder.withTypeText(this.description, true);
            }
            if (this.representingDefaultValue) {
                builder = builder.bold();
            }
            builder = builder.withInsertHandler(this.fileType.newValueInsertHandler());
        } else {
            builder = builder.withRenderer(CUSTOM_SUGGESTION_RENDERER)
                    .withInsertHandler(this.fileType.newKeyInsertHandler());
        }
        return builder;
    }

    public String getFullPath() {
        return dotDelimitedOriginalNames(this.matchesTopFirst);
    }

    public SuggestionNodeType getSuggestionNodeType(final Module module) {
        return this.getLastSuggestionNode().getSuggestionNodeType(module);
    }

    public SuggestionNode getLastSuggestionNode() {
        return this.matchesTopFirst.get(this.matchesTopFirst.size() - 1);
    }

    @Override
    public int compareTo(@NotNull final Suggestion other) {
        final int pathRootToLeafComparisonValue =
                this.pathDotDelimitedRootToLeaf.compareTo(other.pathDotDelimitedRootToLeaf);
        if (pathRootToLeafComparisonValue == 0) {
            return this.suggestionToDisplay.compareTo(other.suggestionToDisplay);
        }
        return pathRootToLeafComparisonValue;
    }

    @NotNull
    public List<? extends OriginalNameProvider> getMatchesForReplacement() {
        if (this.matchesTopFirst.size() > this.numOfAncestors) {
            return this.matchesTopFirst.stream().skip(this.numOfAncestors).collect(toList());
        } else { // can happen when user is trying to select as a child of array, in this case, the suggestion itself becomes the original name
            return singletonList(() -> this.suggestionToDisplay);
        }
    }

}
