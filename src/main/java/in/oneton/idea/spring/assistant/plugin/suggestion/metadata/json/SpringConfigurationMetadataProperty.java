package in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiType;
import in.oneton.idea.spring.assistant.plugin.misc.GenericUtil;
import in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType;
import in.oneton.idea.spring.assistant.plugin.suggestion.clazz.MapClassMetadataProxy;
import in.oneton.idea.spring.assistant.plugin.suggestion.clazz.MetadataProxy;
import in.oneton.idea.spring.assistant.plugin.suggestion.clazz.MetadataProxyInvokerWithReturnValue;
import in.oneton.idea.spring.assistant.plugin.suggestion.completion.FileType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.intellij.codeInsight.documentation.DocumentationManager.createHyperlink;
import static com.intellij.util.containers.ContainerUtil.isEmpty;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.methodForDocumentationNavigation;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.removeGenerics;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.shortenedType;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.updateClassNameAsJavadocHtml;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.safeGetValidType;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.ENUM;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.MAP;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.UNKNOWN_CLASS;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType.VALUES;
import static in.oneton.idea.spring.assistant.plugin.suggestion.clazz.ClassSuggestionNodeFactory.newMetadataProxy;
import static java.util.Comparator.comparing;
import static java.util.Objects.compare;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

/**
 * Refer to https://docs.spring.io/spring-boot/docs/2.0.0.M6/reference/htmlsingle/#configuration-metadata-property-attributes
 */
@EqualsAndHashCode(of = "name")
public class SpringConfigurationMetadataProperty
        implements Comparable<SpringConfigurationMetadataProperty> {

    /**
     * The full name of the PROPERTY. Names are in lower-case period-separated form (for example, server.servlet.path). This attribute is mandatory.
     */
    @Setter
    @Getter
    private String name;
    @Nullable
    @Setter
    @JsonProperty("type")
    private String className;
    @Nullable
    @Setter
    private String description;
    /**
     * The class name of the source that contributed this PROPERTY. For example, if the PROPERTY were from a class annotated with @ConfigurationProperties, this attribute would contain the fully qualified name of that class. If the source type is unknown, it may be omitted.
     */
    @Nullable
    @Setter
    private String sourceType;
    /**
     * Specify whether the PROPERTY is deprecated. If the field is not deprecated or if that information is not known, it may be omitted. The next table offers more detail about the springConfigurationMetadataDeprecation attribute.
     */
    @Nullable
    @Setter
    private SpringConfigurationMetadataDeprecation deprecation;
    /**
     * The default value, which is used if the PROPERTY is not specified. If the type of the PROPERTY is an ARRAY, it can be an ARRAY of value(s). If the default value is unknown, it may be omitted.
     */
    @Nullable
    @Setter
    private Object defaultValue;

    /**
     * Represents either the only hint associated (or) key specific hint when the property represents a map
     */
    @Nullable
    @Expose(deserialize = false)
    private SpringConfigurationMetadataHint genericOrKeyHint;

    /**
     * If the property of type map, the property can have both keys & values. This hint represents value
     */
    @Nullable
    @JsonIgnore
    private SpringConfigurationMetadataHint valueHint;

    /**
     * Responsible for all suggestion queries that needs to be matched against a class
     */
    @Nullable
    private MetadataProxy delegate;

    @Nullable
    private SuggestionNodeType nodeType;
    private boolean delegateCreationAttempted;

    @Nullable
    public List<SuggestionNode> findChildDeepestKeyMatch(final Module module, final List<SuggestionNode> matchesRootTillParentNode,
                                                         final String[] pathSegments, final int pathSegmentStartIndex) {
        if (this.isLeaf(module)) {
            return null;
        }

        if (this.isMapWithPredefinedKeys()) { // map
            assert this.genericOrKeyHint != null;
            final String pathSegment = pathSegments[pathSegmentStartIndex];
            final SpringConfigurationMetadataHintValue hint = this.genericOrKeyHint.findHintValueWithName(pathSegment);

            if (hint != null) {
                matchesRootTillParentNode.add(new HintAwareSuggestionNode(hint));
                final boolean lastPathSegment = pathSegmentStartIndex == pathSegments.length - 1;

                if (lastPathSegment) {
                    return matchesRootTillParentNode;

                } else if (!this.isMapWithPredefinedValues()) {
                    return this.doWithDelegateOrReturnNull(module, metadataProxy -> metadataProxy
                            .findDeepestSuggestionNode(module, matchesRootTillParentNode, pathSegments,
                                    pathSegmentStartIndex));
                }
            }
        }

        return this.doWithDelegateOrReturnNull(module, metadataProxy ->
                metadataProxy.findDeepestSuggestionNode(module, matchesRootTillParentNode, pathSegments, pathSegmentStartIndex));
    }

    @Nullable
    public SortedSet<Suggestion> findChildKeySuggestionsForQueryPrefix(final Module module,
                                                                       final FileType fileType, final List<SuggestionNode> matchesRootTillMe, final int numOfAncestors,
                                                                       final String[] querySegmentPrefixes, final int querySegmentPrefixStartIndex,
                                                                       @Nullable final Set<String> siblingsToExclude) {
        final boolean lastPathSegment = querySegmentPrefixStartIndex == querySegmentPrefixes.length - 1;
        if (lastPathSegment && !this.isLeaf(module)) {
            if (this.isMapWithPredefinedKeys()) { // map
                assert this.genericOrKeyHint != null;
                final String querySegment = querySegmentPrefixes[querySegmentPrefixStartIndex];
                final Collection<SpringConfigurationMetadataHintValue> matches =
                        this.genericOrKeyHint.findHintValuesWithPrefix(querySegment);
                final Stream<SpringConfigurationMetadataHintValue> matchesStream =
                        this.getMatchesAfterExcludingSiblings(this.genericOrKeyHint, matches, siblingsToExclude);

                return matchesStream.map(hintValue -> {
                    final HintAwareSuggestionNode suggestionNode = new HintAwareSuggestionNode(hintValue);
                    return hintValue
                            .buildSuggestionForKey(fileType, matchesRootTillMe, numOfAncestors, suggestionNode,
                                    this.getMapKeyType(module));
                }).collect(toCollection(TreeSet::new));
            } else {
                return this.doWithDelegateOrReturnNull(module, metadataProxy -> metadataProxy
                        .findKeySuggestionsForQueryPrefix(module, fileType, matchesRootTillMe, numOfAncestors,
                                querySegmentPrefixes, querySegmentPrefixStartIndex, siblingsToExclude));
            }
        }
        return null;
    }

    @NotNull
    public Suggestion buildKeySuggestion(final Module module, final FileType fileType, final List<SuggestionNode> matchesRootTillMe, final int numOfAncestors) {

        final Suggestion.SuggestionBuilder builder = Suggestion.builder()
                .suggestionToDisplay(GenericUtil.dotDelimitedOriginalNames(matchesRootTillMe, numOfAncestors))
                .description(this.description)
                .shortType(shortenedType(this.className))
                .defaultValue(this.getDefaultValueAsStr())
                .numOfAncestors(numOfAncestors)
                .matchesTopFirst(matchesRootTillMe)
                .icon(this.getSuggestionNodeType(module).getIcon());

        if (Objects.nonNull(this.deprecation)) {
            builder.deprecationLevel(Objects.nonNull(this.deprecation.getLevel())
                    ? this.deprecation.getLevel()
                    : SpringConfigurationMetadataDeprecationLevel.warning);
        }

        return builder.fileType(fileType).build();
    }

    @NotNull
    public String getDocumentationForKey(final String nodeNavigationPathDotDelimited) {
        // Format for the documentation is as follows
        /*
         * <p><b>a.b.c</b> ({@link com.acme.Generic}<{@link com.acme.Class1}, {@link com.acme.Class2}>)</p>
         * <p><em>Default Value</em> default value</p>
         * <p>Long description</p>
         * or of this type
         * <p><b>Type</b> {@link com.acme.Array}[]</p>
         * <p><b>Declared at</b>{@link com.acme.GenericRemovedClass#method}></p> <-- only for groups with method info
         * <b>WARNING:</b>
         * @deprecated Due to something something. Replaced by <b>c.d.e</b>
         */
        final StringBuilder builder =
                new StringBuilder().append("<b>").append(nodeNavigationPathDotDelimited).append("</b>");

        if (this.className != null) {
            builder.append(" (");
            updateClassNameAsJavadocHtml(builder, this.className);
            builder.append(")");
        }

        if (this.description != null) {
            builder.append("<p>").append(this.description).append("</p>");
        }

        if (this.defaultValue != null) {
            builder.append("<p><em>Default value: </em>").append(this.getDefaultValueAsStr()).append("</p>");
        }

        if (this.sourceType != null) {
            String sourceTypeInJavadocFormat = removeGenerics(this.sourceType);

            // lets show declaration point only if does not match the type
            if (!sourceTypeInJavadocFormat.equals(removeGenerics(this.className))) {
                final StringBuilder buffer = new StringBuilder();
                createHyperlink(buffer, methodForDocumentationNavigation(sourceTypeInJavadocFormat),
                        sourceTypeInJavadocFormat, false);
                sourceTypeInJavadocFormat = buffer.toString();

                builder.append("<p>Declared at ").append(sourceTypeInJavadocFormat).append("</p>");
            }
        }

        if (this.deprecation != null) {
            builder.append("<p><b>").append(this.isDeprecatedError() ?
                    "ERROR: DO NOT USE THIS PROPERTY AS IT IS COMPLETELY UNSUPPORTED" :
                    "WARNING: PROPERTY IS DEPRECATED").append("</b></p>");

            if (this.deprecation.getReason() != null) {
                builder.append("@deprecated Reason: ").append(this.deprecation.getReason());
            }

            if (this.deprecation.getReplacement() != null) {
                builder.append("<p>Replaced by property <b>").append(this.deprecation.getReplacement())
                        .append("</b></p>");
            }
        }

        return builder.toString();
    }

    public boolean isLeaf(final Module module) {
        return this.isLeafWithKnownValues()
                || this.getSuggestionNodeType(module).representsLeaf()
                || this.doWithDelegateOrReturnDefault(module, metadataProxy -> metadataProxy.isLeaf(module), true);
    }

    @NotNull
    public SuggestionNodeType getSuggestionNodeType(final Module module) {
        if (this.nodeType == null) {
            if (this.className != null) {
                this.refreshDelegate(module);

                if (this.delegate != null) {
                    this.nodeType = this.delegate.getSuggestionNodeType(module);
                }

                if (this.nodeType == null) {
                    this.nodeType = UNKNOWN_CLASS;
                }
            } else {
                this.nodeType = SuggestionNodeType.UNDEFINED;
            }
        }

        return this.nodeType;
    }

    public void refreshDelegate(final Module module) {
        if (this.className != null) {
            // Lets update the delegate information only if anything has changed from last time we saw this
            final PsiType type = this.getPsiType(module);
            final boolean validTypeExists = type != null;
            // In the previous refresh, class could not be found. Now class is available in the classpath
            if (validTypeExists && this.delegate == null) {
                this.delegate = newMetadataProxy(module, type);
                // lets force the nodeType to recalculated
                this.nodeType = null;
            }
            // In the previous refresh, class was available in classpath. Now it is no longer available
            if (!validTypeExists && this.delegate != null) {
                this.delegate = null;
                this.nodeType = UNKNOWN_CLASS;
            }
        }
        this.delegateCreationAttempted = true;
    }

    @Override
    public int compareTo(@NotNull final SpringConfigurationMetadataProperty o) {
        return compare(this, o, comparing(thiz -> thiz.name));
    }

    /**
     * @return true if the property is deprecated & level is error, false otherwise
     */
    public boolean isDeprecatedError() {
        return this.deprecation != null && this.deprecation.getLevel() == SpringConfigurationMetadataDeprecationLevel.error;
    }

    public SortedSet<Suggestion> findSuggestionsForValues(final Module module, final FileType fileType,
                                                          final List<SuggestionNode> matchesRootTillContainerProperty, final String prefix,
                                                          @Nullable final Set<String> siblingsToExclude) {
        if (!this.isLeaf(module)) {
            throw new AssertionError();
        }

        if (this.nodeType == VALUES) {
            final Collection<SpringConfigurationMetadataHintValue> matches = requireNonNull(this.genericOrKeyHint).findHintValuesWithPrefix(prefix);
            if (!isEmpty(matches)) {
                final Stream<SpringConfigurationMetadataHintValue> matchesStream =
                        this.getMatchesAfterExcludingSiblings(this.genericOrKeyHint, matches, siblingsToExclude);

                return matchesStream.map(match ->
                                match.buildSuggestionForValue(fileType, matchesRootTillContainerProperty,
                                        this.getDefaultValueAsStr(), this.getPsiType(module)))
                        .collect(toCollection(TreeSet::new));
            }

        } else {
            return this.doWithDelegateOrReturnNull(module, suggestionDelegate ->
                    suggestionDelegate.findValueSuggestionsForPrefix(module, fileType, matchesRootTillContainerProperty, prefix, siblingsToExclude));
        }

        return null;
    }

    public void setGenericOrKeyHint(final SpringConfigurationMetadataHint genericOrKeyHint) {
        this.genericOrKeyHint = genericOrKeyHint;
        this.updateNodeType();
    }

    public void setValueHint(final SpringConfigurationMetadataHint valueHint) {
        this.valueHint = valueHint;
        this.updateNodeType();
    }

    private Stream<SpringConfigurationMetadataHintValue> getMatchesAfterExcludingSiblings(
            @NotNull final SpringConfigurationMetadataHint hintFindValueAgainst,
            final Collection<SpringConfigurationMetadataHintValue> matches,
            @Nullable final Set<String> siblingsToExclude) {
        final Stream<SpringConfigurationMetadataHintValue> matchesStream;
        if (matches == null) {
            return Stream.empty();
        }
        if (siblingsToExclude != null) {
            final Set<SpringConfigurationMetadataHintValue> exclusionMembers =
                    siblingsToExclude.stream().map(hintFindValueAgainst::findHintValueWithName)
                            .collect(toSet());
            matchesStream = matches.stream().filter(value -> !exclusionMembers.contains(value));
        } else {
            matchesStream = matches.stream();
        }
        return matchesStream;
    }

    private void updateNodeType() {
        if (this.isMapWithPredefinedKeys() || this.isMapWithPredefinedValues()) {
            this.nodeType = MAP;
        } else if (this.isLeafWithKnownValues()) {
            this.nodeType = VALUES;
        }
    }

    private PsiType getPsiType(final Module module) {
        if (this.className != null) {
            return safeGetValidType(module, this.className);
        }
        return null;
    }

    private boolean isMapWithPredefinedValues() {
        return this.valueHint != null && this.valueHint.representsValueOfMap();
    }

    private boolean isMapWithPredefinedKeys() {
        return this.genericOrKeyHint != null && this.genericOrKeyHint.representsKeyOfMap();
    }

    private boolean isLeafWithKnownValues() {
        return !this.isMapWithPredefinedKeys() && !this.isMapWithPredefinedValues() && this.genericOrKeyHint != null
                && this.genericOrKeyHint.hasPredefinedValues();
    }

    @Contract("_, _, !null -> !null; _, _, null -> null")
    private <T> T doWithDelegateOrReturnDefault(final Module module,
                                                final MetadataProxyInvokerWithReturnValue<T> invoker, final T defaultValue) {
        final MetadataProxy delegate = this.getDelegate(module);
        if (delegate != null) {
            return invoker.invoke(delegate);
        }
        return defaultValue;
    }

    @Nullable
    private <T> T doWithDelegateOrReturnNull(final Module module,
                                             final MetadataProxyInvokerWithReturnValue<T> invoker) {
        return this.doWithDelegateOrReturnDefault(module, invoker, null);
    }

    private <T> T doWithMapDelegateOrReturnNull(final Module module,
                                                final MetadataProxyInvokerWithReturnValue<T> invoker) {
        final MetadataProxy delegate = this.getDelegate(module);
        if (delegate != null) {
            assert delegate instanceof MapClassMetadataProxy;
            return invoker.invoke((MapClassMetadataProxy) delegate);
        }
        return null;
    }

    private String getDefaultValueAsStr() {
        if (this.defaultValue != null && !(this.defaultValue instanceof Array)
                && !(this.defaultValue instanceof Collection)) {
            if (this.className != null && this.defaultValue instanceof Double) {
                // if defaultValue is a number, its being parsed by gson as double & we will see an incorrect fraction when we take toString()
                switch (this.className) {
                    case "java.lang.Integer":
                        return Integer.toString(((Double) this.defaultValue).intValue());
                    case "java.lang.Byte":
                        return Byte.toString(((Double) this.defaultValue).byteValue());
                    case "java.lang.Short":
                        return Short.toString(((Double) this.defaultValue).shortValue());
                }
            }
            return this.defaultValue.toString();
        }
        return null;
    }

    @Nullable
    private MetadataProxy getDelegate(final Module module) {
        if (!this.delegateCreationAttempted) {
            this.refreshDelegate(module);
        }
        return this.delegate;
    }

    @Nullable
    private PsiType getMapKeyType(final Module module) {
        final SuggestionNodeType nodeType = this.getSuggestionNodeType(module);
        if (nodeType == MAP) {
            return this.doWithDelegateOrReturnNull(module, delegate -> {
                assert delegate instanceof MapClassMetadataProxy;
                return ((MapClassMetadataProxy) delegate).getMapKeyType(module);
            });
        }
        return null;
    }

    @Nullable
    private PsiType getMapValueType(final Module module) {
        final SuggestionNodeType nodeType = this.getSuggestionNodeType(module);
        if (nodeType == MAP) {
            return this.doWithDelegateOrReturnNull(module, delegate -> {
                assert delegate instanceof MapClassMetadataProxy;
                return ((MapClassMetadataProxy) delegate).getMapValueType(module);
            });
        }
        return null;
    }

    public String getDocumentationForValue(final Module module, final String nodeNavigationPathDotDelimited,
                                           final String value) {
        if (this.isLeafWithKnownValues()) {
            assert this.genericOrKeyHint != null;
            final SpringConfigurationMetadataHintValue hintValueWithName =
                    this.genericOrKeyHint.findHintValueWithName(value);
            if (hintValueWithName != null) {
                return hintValueWithName
                        .getDocumentationForValue(nodeNavigationPathDotDelimited, this.getMapValueType(module));
            }
        } else {
            // possible this represents an enum
            return this.doWithDelegateOrReturnNull(module, delegate -> delegate
                    .getDocumentationForValue(module, nodeNavigationPathDotDelimited, value));
        }
        return null;
    }


    class HintAwareSuggestionNode implements SuggestionNode {

        private final SpringConfigurationMetadataHintValue target;

        /**
         * @param target hint value
         */
        HintAwareSuggestionNode(final SpringConfigurationMetadataHintValue target) {
            this.target = target;
        }

        @Nullable
        @Override
        public List<SuggestionNode> findDeepestSuggestionNode(final Module module,
                                                              final List<SuggestionNode> matchesRootTillParentNode, final String[] pathSegments,
                                                              final int pathSegmentStartIndex) {
            throw new IllegalAccessError("Should never be called");
        }

        @Nullable
        @Override
        public SortedSet<Suggestion> findKeySuggestionsForQueryPrefix(final Module module, final FileType fileType,
                                                                      final List<SuggestionNode> matchesRootTillMe, final int numOfAncestors, final String[] querySegmentPrefixes,
                                                                      final int querySegmentPrefixStartIndex) {
            return SpringConfigurationMetadataProperty.this.doWithMapDelegateOrReturnNull(module,
                    delegate -> ((MapClassMetadataProxy) delegate)
                            .findChildKeySuggestionForQueryPrefix(module, fileType, matchesRootTillMe,
                                    numOfAncestors, querySegmentPrefixes, querySegmentPrefixStartIndex));
        }

        @Nullable
        @Override
        public SortedSet<Suggestion> findKeySuggestionsForQueryPrefix(final Module module, final FileType fileType,
                                                                      final List<SuggestionNode> matchesRootTillMe, final int numOfAncestors, final String[] querySegmentPrefixes,
                                                                      final int querySegmentPrefixStartIndex, @Nullable final Set<String> siblingsToExclude) {
            return SpringConfigurationMetadataProperty.this.doWithMapDelegateOrReturnNull(module,
                    delegate -> ((MapClassMetadataProxy) delegate)
                            .findChildKeySuggestionForQueryPrefix(module, fileType, matchesRootTillMe,
                                    numOfAncestors, querySegmentPrefixes, querySegmentPrefixStartIndex,
                                    siblingsToExclude));
        }

        @Override
        public boolean supportsDocumentation() {
            return true;
        }

        @Override
        public String getOriginalName() {
            return this.target.toString();
        }

        @Nullable
        @Override
        public String getNameForDocumentation(final Module module) {
            return this.getOriginalName();
        }

        @Nullable
        @Override
        public String getDocumentationForKey(final Module module, final String nodeNavigationPathDotDelimited) {
            return this.target
                    .getDocumentationForKey(module, nodeNavigationPathDotDelimited, SpringConfigurationMetadataProperty.this.getDelegate(module));
        }

        @Nullable
        @Override
        public SortedSet<Suggestion> findValueSuggestionsForPrefix(final Module module, final FileType fileType,
                                                                   final List<SuggestionNode> matchesRootTillMe, final String prefix) {
            if (SpringConfigurationMetadataProperty.this.isMapWithPredefinedValues()) {
                assert SpringConfigurationMetadataProperty.this.valueHint != null;
                final Collection<SpringConfigurationMetadataHintValue> matches =
                        SpringConfigurationMetadataProperty.this.valueHint.findHintValuesWithPrefix(prefix);
                if (matches != null && matches.size() != 0) {
                    return matches.stream().map(match -> match
                            .buildSuggestionForValue(fileType, matchesRootTillMe, SpringConfigurationMetadataProperty.this.getDefaultValueAsStr(),
                                    SpringConfigurationMetadataProperty.this.getMapValueType(module))).collect(toCollection(TreeSet::new));
                }
            } else {
                return SpringConfigurationMetadataProperty.this.doWithDelegateOrReturnNull(module, delegate -> delegate
                        .findValueSuggestionsForPrefix(module, fileType, matchesRootTillMe, prefix));
            }
            return null;
        }

        @Nullable
        @Override
        public SortedSet<Suggestion> findValueSuggestionsForPrefix(final Module module, final FileType fileType,
                                                                   final List<SuggestionNode> matchesRootTillMe, final String prefix,
                                                                   @Nullable final Set<String> siblingsToExclude) {
            if (SpringConfigurationMetadataProperty.this.isMapWithPredefinedValues()) {
                assert SpringConfigurationMetadataProperty.this.valueHint != null;
                final Collection<SpringConfigurationMetadataHintValue> matches =
                        SpringConfigurationMetadataProperty.this.valueHint.findHintValuesWithPrefix(prefix);
                if (!isEmpty(matches)) {
                    final Stream<SpringConfigurationMetadataHintValue> matchesStream =
                            SpringConfigurationMetadataProperty.this.getMatchesAfterExcludingSiblings(SpringConfigurationMetadataProperty.this.valueHint, matches, siblingsToExclude);
                    return matchesStream.map(match -> match
                            .buildSuggestionForValue(fileType, matchesRootTillMe, SpringConfigurationMetadataProperty.this.getDefaultValueAsStr(),
                                    SpringConfigurationMetadataProperty.this.getMapValueType(module))).collect(toCollection(TreeSet::new));
                }
            } else {
                return SpringConfigurationMetadataProperty.this.doWithDelegateOrReturnNull(module, delegate -> delegate
                        .findValueSuggestionsForPrefix(module, fileType, matchesRootTillMe, prefix));
            }
            return null;
        }

        @Nullable
        @Override
        public String getDocumentationForValue(final Module module, final String nodeNavigationPathDotDelimited,
                                               final String originalValue) {
            if (SpringConfigurationMetadataProperty.this.isMapWithPredefinedValues()) {
                assert SpringConfigurationMetadataProperty.this.valueHint != null;
                final Collection<SpringConfigurationMetadataHintValue> matches =
                        SpringConfigurationMetadataProperty.this.valueHint.findHintValuesWithPrefix(originalValue);
                assert matches != null && matches.size() == 1;
                final SpringConfigurationMetadataHintValue hint = matches.iterator().next();
                return hint
                        .getDocumentationForValue(nodeNavigationPathDotDelimited, SpringConfigurationMetadataProperty.this.getMapValueType(module));
            } else {
                return SpringConfigurationMetadataProperty.this.doWithDelegateOrReturnNull(module, delegate -> delegate
                        .getDocumentationForValue(module, nodeNavigationPathDotDelimited, originalValue));
            }
        }

        @Override
        public boolean isLeaf(final Module module) {
            if (SpringConfigurationMetadataProperty.this.isLeafWithKnownValues() || SpringConfigurationMetadataProperty.this.isMapWithPredefinedValues()) {
                return true;
            }
            // whether the node is a leaf or not depends on the value of the map that containing property points to
            return PsiCustomUtil.getSuggestionNodeType(SpringConfigurationMetadataProperty.this.getMapValueType(module)).representsLeaf();
        }

        @Override
        public boolean isMetadataNonProperty() {
            return false;
        }

        @NotNull
        @Override
        public SuggestionNodeType getSuggestionNodeType(final Module module) {
            if (SpringConfigurationMetadataProperty.this.isLeafWithKnownValues() || SpringConfigurationMetadataProperty.this.isMapWithPredefinedValues()) { // predefined values
                return ENUM;
            }
            return PsiCustomUtil.getSuggestionNodeType(SpringConfigurationMetadataProperty.this.getMapValueType(module));
        }

    }

}
