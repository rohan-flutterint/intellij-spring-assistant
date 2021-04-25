package in.oneton.idea.spring.assistant.plugin.suggestion.metadata;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiType;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType;
import in.oneton.idea.spring.assistant.plugin.suggestion.completion.FileType;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataGroup;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.newListWithMembers;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.newSingleElementSortedSet;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.safeGetValidType;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

/**
 * Represents a node in the hierarchy of suggestions
 * Useful for navigating all suggestions & also acts as source of truth.
 * Note that some of the intermediate nodes might be there to just support the hierarchy
 * <p>
 * Also used for dot delimited search search. Each element corresponds to only a single section of the complete suggestion hierarchy
 * i.e if the we are building suggestions for
 * <ul>
 * <li>alpha.childNode11.charlie</li>
 * <li>alpha.childNode12.echo</li>
 * <li>alpha.echo</li>
 * <li>childNode11.charlie</li>
 * <li>childNode11.childNode12</li>
 * </ul>
 * <p>
 * The search for above properties would look like
 * <ul>
 * <li>
 * alpha
 * <ul>
 * <li>
 * childNode11
 * <ul>
 * <li>charlie</li>
 * </ul>
 * </li>
 * <li>
 * childNode12
 * <ul>
 * <li>echo</li>
 * </ul>
 * </li>
 * </ul>
 * </li>
 * <li>echo</li>
 * <li>
 * childNode11
 * <ul>
 * <li>charlie</li>
 * <li>childNode12</li>
 * </ul>
 * </li>
 * </ul>
 * <p>
 * We can expect a total of 5 tries in the complete search tree, each child trie being hosted by the parent trie element (+ a special toplevel trie for the whole tree)
 * <ul>
 * <li>(alpha + echo + childNode11) - for top level elements</li>
 * <li>alpha > (childNode11 + childNode12) - for children of <em>alpha</em></li>
 * <li>alpha > childNode11 > (charlie) - for children of <em>alpha > childNode11</em></li>
 * <li>alpha > childNode12 > (echo) - for children of <em>alpha > childNode12</em></li>
 * <li>childNode11 > (charlie + childNode12) - for children of <em>childNode11</em></li>
 * </ul>
 * <p>
 * <b>NOTE:</b> elements within the trie are indicated by enclosing them in curved brackets
 * <p>
 * <p>
 * This hierarchical trie is useful for searches like `a.ch.c` to correspond to `alpha.childNode11.charlie`
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class MetadataNonPropertySuggestionNode extends MetadataSuggestionNode {

    private static final Logger log = Logger.getInstance(MetadataNonPropertySuggestionNode.class);

    /**
     * Sanitised name used for lookup. `-`, `_` are removed, upper cased characters are converted to lower case
     */
    @EqualsAndHashCode.Include
    private String name;
    /**
     * Section of the group/PROPERTY name. Sole purpose of this is to split all properties into their individual part
     */
    @ToString.Include
    private String originalName;

    // TODO: Make sure that this will be part of search only if type & sourceType are part of the class path
    /**
     * Can be null for intermediate nodes that dont have a group entry in `spring-configuration-metadata.json`
     */
    @Nullable
    private SpringConfigurationMetadataGroup group;

    /**
     * Parent reference, for bidirectional navigation. Can be null for roots
     */
    @Nullable
    private MetadataNonPropertySuggestionNode parent;
    /**
     * Set of sources these suggestions belong to
     */
    private Set<String> belongsTo;
    /**
     * Child name -> child node. Aids in quick lookup. NOTE: All keys are sanitized
     */
    @Nullable
    private Map<String, MetadataSuggestionNode> childLookup;

    /**
     * Child trie for the nodes at next level, aids in prefix based searching. NOTE: All keys are sanitized
     */
    @Nullable
    private Trie<String, MetadataSuggestionNode> childrenTrie;

    /**
     * @param originalName name that is not sanitised
     * @param parent       parent MetadataNonPropertySuggestionNode node
     * @param belongsTo    file/jar containing this property
     * @return newly constructed group node
     */
    public static MetadataNonPropertySuggestionNode newInstance(final String originalName,
                                                                @Nullable final MetadataNonPropertySuggestionNode parent, final String belongsTo) {
        final MetadataNonPropertySuggestionNodeBuilder builder =
                MetadataNonPropertySuggestionNode.builder().name(SuggestionNode.sanitise(originalName))
                        .originalName(originalName).parent(parent);
        final Set<String> belongsToSet = new THashSet<>();
        belongsToSet.add(belongsTo);
        builder.belongsTo(belongsToSet);
        return builder.build();
    }

    @Override
    public MetadataSuggestionNode findDeepestMetadataNode(final String[] pathSegments, final int pathSegmentStartIndex, final boolean matchAllSegments) {

        MetadataSuggestionNode deepestMatch = null;
        if (!matchAllSegments) {
            deepestMatch = this;
        }

        final boolean haveMoreSegments = pathSegmentStartIndex < pathSegments.length;
        if (!haveMoreSegments) {
            return deepestMatch;
        }

        final boolean lastSegment = pathSegmentStartIndex == (pathSegments.length - 1);
        final String pathSegment = pathSegments[pathSegmentStartIndex];

        if (this.hasChildren()) {
            assert this.childLookup != null;
            if (this.childLookup.containsKey(pathSegment)) {
                final MetadataSuggestionNode child = this.childLookup.get(pathSegment);
                if (lastSegment) {
                    deepestMatch = child;
                } else {
                    deepestMatch = child.findDeepestMetadataNode(pathSegments, pathSegmentStartIndex + 1, matchAllSegments);
                }

                if (matchAllSegments && deepestMatch == null) {
                    deepestMatch = this;
                }
            }

        } else if (lastSegment && this.name.equals(pathSegment)) {
            deepestMatch = this;
        }
        return deepestMatch;
    }


    @Nullable
    @Override
    public List<SuggestionNode> findDeepestSuggestionNode(final Module module,
                                                          final List<SuggestionNode> matchesRootTillMe, final String[] pathSegments, final int pathSegmentStartIndex) {
        List<SuggestionNode> deepestMatch = null;
        final boolean haveMoreSegments = pathSegmentStartIndex < pathSegments.length;
        if (haveMoreSegments) {
            final String currentPathSegment = pathSegments[pathSegmentStartIndex];
            final boolean lastSegment = pathSegmentStartIndex == (pathSegments.length - 1);
            if (this.hasChildren()) {
                assert this.childLookup != null;
                if (this.childLookup.containsKey(currentPathSegment)) {
                    final MetadataSuggestionNode child = this.childLookup.get(currentPathSegment);
                    matchesRootTillMe.add(child);
                    if (lastSegment) {
                        deepestMatch = matchesRootTillMe;
                    } else {
                        deepestMatch = child.findDeepestSuggestionNode(module, matchesRootTillMe, pathSegments,
                                pathSegmentStartIndex + 1);
                    }
                }
            } else if (lastSegment && this.name.equals(currentPathSegment)) {
                deepestMatch = matchesRootTillMe;
            }
        } else {
            deepestMatch = matchesRootTillMe;
        }

        return deepestMatch;
    }

    public void addChildren(final Module module, final SpringConfigurationMetadataGroup group,
                            final String[] rawPathSegments, final int startIndex, final String belongsTo) {
        final MetadataNonPropertySuggestionNode groupNode =
                this.addChildren(rawPathSegments, startIndex, rawPathSegments.length - 1, belongsTo);
        groupNode.setGroup(module, group);
    }

    public void addChildren(final SpringConfigurationMetadataProperty property, final String[] rawPathSegments,
                            final int startIndex, final String belongsTo) {
        final MetadataNonPropertySuggestionNode parentNode;
        // since last property is the actual property, lets only add children only till last but one
        final int endIndexIncl = rawPathSegments.length - 2;
        if (startIndex <= endIndexIncl) {
            parentNode = this.addChildren(rawPathSegments, startIndex, endIndexIncl, belongsTo);
        } else {
            parentNode = this;
            this.addRefCascadeTillRoot(belongsTo);
        }

        parentNode.addProperty(property, rawPathSegments[rawPathSegments.length - 1], belongsTo);
    }

    @Override
    @Nullable
    public SortedSet<Suggestion> findKeySuggestionsForQueryPrefix(final Module module, final FileType fileType,
                                                                  final List<SuggestionNode> matchesRootTillMe,
                                                                  final int numOfAncestors, final String[] querySegmentPrefixes,
                                                                  final int querySegmentPrefixStartIndex,
                                                                  @Nullable final Set<String> siblingsToExclude) {

        final boolean lookingForConcreteNode = querySegmentPrefixStartIndex >= querySegmentPrefixes.length;
        if (lookingForConcreteNode) {
            return this.lookingForConcreteNode(module, fileType, matchesRootTillMe, numOfAncestors, querySegmentPrefixes, querySegmentPrefixStartIndex);

        } else if (this.hasChildren()) {
            return this.getSuggestionsChildren(module, fileType, matchesRootTillMe, numOfAncestors, querySegmentPrefixes, querySegmentPrefixStartIndex, siblingsToExclude);
        }
        return null;
    }

    private SortedSet<Suggestion> getSuggestionsChildren(final Module module, final FileType fileType, final List<SuggestionNode> matchesRootTillMe,
                                                         final int numOfAncestors, final String[] querySegmentPrefixes, final int querySegmentPrefixStartIndex,
                                                         final @org.jetbrains.annotations.Nullable Set<String> siblingsToExclude) {
        assert this.childrenTrie != null;
        assert this.childLookup != null;
        final String querySegmentPrefix = querySegmentPrefixes[querySegmentPrefixStartIndex];
        final SortedMap<String, MetadataSuggestionNode> sortedPrefixToMetadataNode =
                this.childrenTrie.prefixMap(querySegmentPrefix);
        Collection<MetadataSuggestionNode> matchedChildren = sortedPrefixToMetadataNode.values();

        Set<MetadataSuggestionNode> exclusionMembers = null;
        if (siblingsToExclude != null) {
            exclusionMembers = siblingsToExclude.stream().map(this.childLookup::get).collect(toSet());
        }

        if (!isEmpty(exclusionMembers) && !isEmpty(matchedChildren)) {
            final Set<MetadataSuggestionNode> finalExclusionMembers = exclusionMembers;
            matchedChildren = matchedChildren.stream()
                    .filter(value -> !finalExclusionMembers.contains(value))
                    .collect(toList());
        }

        int segmentPrefixStartIndex = querySegmentPrefixStartIndex;
        if (matchedChildren.size() == 0) {
            matchedChildren = this.computeChildrenToIterateOver(this.childLookup, exclusionMembers);
        } else {
            segmentPrefixStartIndex = segmentPrefixStartIndex + 1;
        }
        // lets search in the next level
        return this.addChildToMatchesAndSearchInNextLevel(module, fileType, matchesRootTillMe, numOfAncestors,
                querySegmentPrefixes, segmentPrefixStartIndex, matchedChildren);
    }

    private SortedSet<Suggestion> lookingForConcreteNode(final Module module, final FileType fileType, final List<SuggestionNode> matchesRootTillMe, final int numOfAncestors, final String[] querySegmentPrefixes, final int querySegmentPrefixStartIndex) {
        if (this.isGroup()) {
            // If we have only one child, lets send the child value directly instead of this node. This way user does not need trigger suggestion for level, esp. when we know there will is only be one child
            if (this.hasOnlyOneChild()) {
                assert this.childrenTrie != null;
                return this.addChildToMatchesAndSearchInNextLevel(module, fileType, matchesRootTillMe,
                        numOfAncestors, querySegmentPrefixes, querySegmentPrefixStartIndex,
                        this.childrenTrie.values());
            } else { // either there are no children/multiple children are present. Lets return suggestions
                assert this.group != null;
                return newSingleElementSortedSet(
                        this.group.newSuggestion(fileType, matchesRootTillMe, numOfAncestors));
            }
        } else { // intermediate node, lets get all next level groups & properties
            assert this.childrenTrie != null;
            return this.addChildToMatchesAndSearchInNextLevel(module, fileType, matchesRootTillMe,
                    numOfAncestors, querySegmentPrefixes, querySegmentPrefixStartIndex,
                    this.childrenTrie.values());
        }
    }

    @Override
    protected boolean hasOnlyOneChild() {
        return this.childrenTrie != null && this.childrenTrie.size() == 1;
        //     && childrenTrie.values().stream()
        //        .allMatch(MetadataSuggestionNode::hasOnlyOneChild)
    }

    @Override
    public String toTree() {
        final StringBuilder builder = new StringBuilder(this.originalName)
                .append(this.isRoot() ? "(root + group)" : (this.isGroup() ? "(group)" : "(intermediate)"))
                .append("\n");
        if (this.childLookup != null) {
            this.childLookup.forEach(
                    (k, v) -> builder.append(v.toTree().trim().replaceAll("\\^", "  ").replaceAll("\n", "\n  "))
                            .append("\n"));
        }
        return builder.toString();
    }

    /**
     * @param containerPath Represents path to the metadata file container
     * @return true if no children left & this item does not belong to any other source
     */
    @Override
    public boolean removeRefCascadeDown(final String containerPath) {
        this.belongsTo.remove(containerPath);
        // If the current node & all its children belong to a single file, lets remove the whole tree
        if (this.belongsTo.size() == 0) {
            return true;
        }

        if (this.hasChildren()) {
            assert this.childLookup != null;
            assert this.childrenTrie != null;
            final Iterator<MetadataSuggestionNode> iterator = this.childrenTrie.values().iterator();
            while (iterator.hasNext()) {
                final MetadataSuggestionNode child = iterator.next();
                final boolean canRemoveReference = child.removeRefCascadeDown(containerPath);
                if (canRemoveReference) {
                    iterator.remove();
                    this.childLookup.remove(child.getName());
                    this.childrenTrie.remove(child.getName());
                }
            }
            if (!this.hasChildren()) {
                this.childLookup = null;
                this.childrenTrie = null;
            }
        }
        return false;
    }

    @Override
    protected boolean isRoot() {
        return this.parent == null;
    }

    @Override
    public boolean isGroup() {
        return this.group != null;
    }

    @Override
    public boolean isProperty() {
        return false;
    }

    @NotNull
    @Override
    public String getDocumentationForKey(final Module module, final String nodeNavigationPathDotDelimited) {
        if (this.isGroup()) {
            assert this.group != null;
            return this.group.getDocumentation(nodeNavigationPathDotDelimited);
        }
        throw new RuntimeException(
                "Documentation not supported for this element. Call supportsDocumentation() first");
    }

    @Nullable
    @Override
    public SortedSet<Suggestion> findValueSuggestionsForPrefix(final Module module, final FileType fileType,
                                                               final List<SuggestionNode> matchesRootTillMe, final String prefix,
                                                               @Nullable final Set<String> siblingsToExclude) {
        throw new IllegalAccessError("Should never be called");
    }

    @Nullable
    @Override
    public String getDocumentationForValue(final Module module, final String nodeNavigationPathDotDelimited,
                                           final String originalValue) {
        throw new IllegalAccessError("Should never be called");
    }

    @Override
    public boolean isLeaf(final Module module) {
        return false;
    }

    @Override
    public boolean isMetadataNonProperty() {
        return true;
    }

    private boolean hasChildren() {
        return this.childrenTrie != null && this.childrenTrie.size() != 0;
    }

    @NotNull
    @Override
    public SuggestionNodeType getSuggestionNodeType(final Module module) {
        if (this.isGroup()) {
            assert this.group != null;
            return this.group.getNodeType();
        } else {
            return SuggestionNodeType.UNDEFINED;
        }
    }

    public void setGroup(final Module module, final SpringConfigurationMetadataGroup group) {
        this.updateGroupType(module, group);
        this.group = group;
    }

    @Override
    public void refreshClassProxy(final Module module) {
        this.updateGroupType(module, this.group);
        if (this.hasChildren()) {
            assert this.childLookup != null;
            this.childLookup.values().forEach(child -> child.refreshClassProxy(module));
        }
    }

    private Collection<MetadataSuggestionNode> computeChildrenToIterateOver(@NotNull final Map<String, MetadataSuggestionNode> childLookup,
                                                                            final Set<MetadataSuggestionNode> exclusionMembers) {
        if (CollectionUtils.isEmpty(exclusionMembers)) {
            return childLookup.values();
        } else {
            return childLookup.values().stream()
                    .filter(value -> !exclusionMembers.contains(value))
                    .collect(toList());
        }
    }

    private void addProperty(final SpringConfigurationMetadataProperty property, final String originalName, final String belongsTo) {
        this.addRefCascadeTillRoot(belongsTo);
        if (!this.hasChildren()) {
            this.childLookup = new THashMap<>();
            this.childrenTrie = new PatriciaTrie<>();
        }

        assert this.childLookup != null;
        assert this.childrenTrie != null;
        final MetadataSuggestionNode childNode = MetadataPropertySuggestionNode.newInstance(originalName, property, this, belongsTo);

        final String nameSanitised = SuggestionNode.sanitise(originalName);
        this.childLookup.put(nameSanitised, childNode);
        this.childrenTrie.put(nameSanitised, childNode);
    }

    private MetadataNonPropertySuggestionNode addChildren(final String[] rawPathSegments, final int startIndex,
                                                          final int endIndexIncl, final String belongsTo) {
        this.addRefCascadeTillRoot(belongsTo);
        if (!this.hasChildren()) {
            this.childLookup = new THashMap<>();
            this.childrenTrie = new PatriciaTrie<>();
        }

        assert this.childLookup != null;
        assert this.childrenTrie != null;

        final String rawPathSegment = rawPathSegments[startIndex];
        final String pathSegment = SuggestionNode.sanitise(rawPathSegment);

        var childNode = (MetadataNonPropertySuggestionNode) this.childLookup.get(pathSegment);
        if (childNode == null) {
            childNode = MetadataNonPropertySuggestionNode.newInstance(rawPathSegment, this, belongsTo);
            childNode.setParent(this);

            this.childLookup.put(pathSegment, childNode);
            this.childrenTrie.put(pathSegment, childNode);
        }
        // If this is the last segment, lets set group
        return startIndex >= endIndexIncl ? childNode : childNode.addChildren(rawPathSegments, startIndex + 1, endIndexIncl, belongsTo);
    }

    private SortedSet<Suggestion> addChildToMatchesAndSearchInNextLevel(final Module module,
                                                                        final FileType fileType, final List<SuggestionNode> matchesRootTillParentNode, final int numOfAncestors,
                                                                        final String[] querySegmentPrefixes, final int querySegmentPrefixStartIndex,
                                                                        final Collection<MetadataSuggestionNode> childNodes) {
        SortedSet<Suggestion> suggestions = null;
        for (final MetadataSuggestionNode child : childNodes) {
            final List<SuggestionNode> matchesRootTillChild =
                    unmodifiableList(newListWithMembers(matchesRootTillParentNode, child));
            final Set<Suggestion> matchedSuggestions = child
                    .findKeySuggestionsForQueryPrefix(module, fileType, matchesRootTillChild, numOfAncestors,
                            querySegmentPrefixes, querySegmentPrefixStartIndex, null);
            if (matchedSuggestions != null) {
                if (suggestions == null) {
                    suggestions = new TreeSet<>();
                }
                suggestions.addAll(matchedSuggestions);
            }
        }
        return suggestions;
    }

    private void updateGroupType(final Module module, final SpringConfigurationMetadataGroup group) {
        if (group != null && group.getClassName() != null) {
            final PsiType groupPsiType = safeGetValidType(module, group.getClassName());
            if (groupPsiType != null) {
                group.setNodeType(PsiCustomUtil.getSuggestionNodeType(groupPsiType));
            } else {
                group.setNodeType(SuggestionNodeType.UNKNOWN_CLASS);
            }
        }
    }

}
