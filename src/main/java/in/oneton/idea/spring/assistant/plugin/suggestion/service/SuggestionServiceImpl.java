package in.oneton.idea.spring.assistant.plugin.suggestion.service;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.LogUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import in.oneton.idea.spring.assistant.plugin.initializr.misc.JsonUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.completion.FileType;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.MetadataContainerInfo;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.MetadataNonPropertySuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.MetadataPropertySuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.MetadataSuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadata;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataGroup;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataHint;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataProperty;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.apache.commons.lang.time.StopWatch;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Future;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.modifiableList;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.truncateIdeaDummyIdentifier;
import static in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion.PERIOD_DELIMITER;
import static in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode.sanitise;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class SuggestionServiceImpl implements SuggestionService {

    private static final Logger log = Logger.getInstance(SuggestionServiceImpl.class);

    private final Module module;
    private final Map<String, MetadataContainerInfo> moduleNameToSeenContainerPathToContainerInfo;

    /**
     * Within the trie, all keys are stored in sanitised format to enable us find keys without worrying about hyphens, underscores, e.t.c in the keys themselves
     */
    private final Trie<String, MetadataSuggestionNode> moduleNameToRootSearchIndex;
    private Future<?> currentExecution;
    private volatile boolean indexingInProgress;

    SuggestionServiceImpl(final Module module) {
        this.module = module;
        this.moduleNameToSeenContainerPathToContainerInfo = new THashMap<>();
        this.moduleNameToRootSearchIndex = new PatriciaTrie<>();
    }

    private static String[] toSanitizedPathSegments(final String element) {
        final String[] splits = element.trim().split(PERIOD_DELIMITER, -1);
        for (int i = 0; i < splits.length; i++) {
            splits[i] = sanitise(splits[i]);
        }
        return splits;
    }

    private static String[] toRawPathSegments(final String element) {
        final String[] splits = element.trim().split(PERIOD_DELIMITER, -1);
        for (int i = 0; i < splits.length; i++) {
            splits[i] = splits[i].trim();
        }
        return splits;
    }

    @Override
    public void index() {
        if (this.currentExecution != null && !this.currentExecution.isDone()) {
            this.currentExecution.cancel(false);
        }

        this.currentExecution = getApplication().executeOnPooledThread(() ->
                DumbService.getInstance(this.module.getProject()).runReadActionInSmartMode(() -> {
                    LogUtil.debug(() -> log.debug("--> Indexing requested for module " + this.module.getName()));

                    final StopWatch moduleTimer = new StopWatch();
                    moduleTimer.start();

                    try {
                        this.indexingInProgress = false;
                        final OrderEnumerator moduleOrderEnumerator = OrderEnumerator.orderEntries(this.module);
                        final List<MetadataContainerInfo> newModuleContainersToProcess = this.computeNewContainersToProcess(
                                moduleOrderEnumerator);
                        final List<MetadataContainerInfo> moduleContainersToRemove = this.computeContainersToRemove(moduleOrderEnumerator);
                        this.processContainers(newModuleContainersToProcess, moduleContainersToRemove);
                        this.indexingInProgress = true;

                    } finally {
                        moduleTimer.stop();
                        LogUtil.debug(() -> log.debug("<-- Indexing took " + moduleTimer + " for module " + this.module.getName()));
                    }
                })
        );
    }

    @Nullable
    @Override
    public List<SuggestionNode> findMatchedNodesRootTillEnd(final List<String> containerElements) {
        final String[] pathSegments = containerElements
                .stream()
                .flatMap(element -> stream(toSanitizedPathSegments(element)))
                .toArray(String[]::new);

        final MetadataSuggestionNode searchStartNode = this.moduleNameToRootSearchIndex.get(pathSegments[0]);
        if (Objects.nonNull(searchStartNode)) {
            final List<SuggestionNode> matches = modifiableList(searchStartNode);
            if (pathSegments.length > 1) {
                return searchStartNode.findDeepestSuggestionNode(this.module, matches, pathSegments, 1);
            }

            return matches;
        }
        return null;
    }

    @Override
    public boolean canProvideSuggestions() {
        return this.indexingInProgress && !this.moduleNameToRootSearchIndex.isEmpty();
    }

    @Override
    public List<LookupElement> findSuggestionsForQueryPrefix(final FileType fileType, final PsiElement element,
                                                             @Nullable final List<String> ancestralKeys,
                                                             final String queryWithDotDelimitedPrefixes,
                                                             @Nullable final Set<String> siblingsToExclude) {

        LogUtil.debug(() -> log.debug("Search requested for " + queryWithDotDelimitedPrefixes));

        final StopWatch timer = new StopWatch();
        timer.start();

        try {
            final String[] querySegmentPrefixes = toSanitizedPathSegments(queryWithDotDelimitedPrefixes);
            Set<Suggestion> suggestions = null;

            if (ancestralKeys != null) {
                final String[] ancestralKeySegments = ancestralKeys.stream()
                        .flatMap(key -> stream(toRawPathSegments(key)))
                        .toArray(String[]::new);

                final MetadataSuggestionNode rootNode = this.moduleNameToRootSearchIndex.get(sanitise(ancestralKeySegments[0]));
                if (rootNode != null) {
                    final List<SuggestionNode> matchesRootToDeepest;
                    SuggestionNode startSearchFrom = null;
                    if (ancestralKeySegments.length > 1) {
                        final String[] sanitisedAncestralPathSegments = stream(ancestralKeySegments)
                                .map(SuggestionNode::sanitise)
                                .toArray(String[]::new);

                        matchesRootToDeepest = rootNode.findDeepestSuggestionNode(this.module, modifiableList(rootNode),
                                sanitisedAncestralPathSegments, 1);

                        if (CollectionUtils.isNotEmpty(matchesRootToDeepest)) {
                            startSearchFrom = matchesRootToDeepest.get(matchesRootToDeepest.size() - 1);
                        }

                    } else {
                        startSearchFrom = rootNode;
                        matchesRootToDeepest = singletonList(rootNode);
                    }

                    if (startSearchFrom != null) {
                        // if search start node is a leaf, this means, the user is looking for values for the given key, lets find the suggestions for values
                        if (startSearchFrom.isLeaf(this.module)) {
                            suggestions = startSearchFrom.findValueSuggestionsForPrefix(this.module, fileType,
                                    unmodifiableList(matchesRootToDeepest),
                                    sanitise(truncateIdeaDummyIdentifier(element.getText())), siblingsToExclude);
                        } else {
                            suggestions = startSearchFrom.findKeySuggestionsForQueryPrefix(this.module, fileType,
                                    unmodifiableList(matchesRootToDeepest), matchesRootToDeepest.size(),
                                    querySegmentPrefixes, 0, siblingsToExclude);
                        }
                    }
                }

            } else {
                final String rootQuerySegmentPrefix = querySegmentPrefixes[0];
                final SortedMap<String, MetadataSuggestionNode> topLevelQueryResults = this.moduleNameToRootSearchIndex.prefixMap(rootQuerySegmentPrefix);

                final Collection<MetadataSuggestionNode> childNodes;
                final int querySegmentPrefixStartIndex;

                // If no results are found at the top level, let dive deeper and find matches
                if (topLevelQueryResults == null || topLevelQueryResults.size() == 0) {
                    childNodes = this.moduleNameToRootSearchIndex.values();
                    querySegmentPrefixStartIndex = 0;
                } else {
                    childNodes = topLevelQueryResults.values();
                    querySegmentPrefixStartIndex = 1;
                }

                final Collection<MetadataSuggestionNode> nodesToSearchAgainst;
                if (siblingsToExclude != null) {
                    final Set<MetadataSuggestionNode> nodesToExclude = siblingsToExclude
                            .stream()
                            .flatMap(exclude -> this.moduleNameToRootSearchIndex.prefixMap(exclude).values().stream())
                            .collect(toSet());

                    nodesToSearchAgainst = childNodes.stream()
                            .filter(node -> !nodesToExclude.contains(node))
                            .collect(toList());
                } else {
                    nodesToSearchAgainst = childNodes;
                }

                suggestions = this.doFindSuggestionsForQueryPrefix(fileType, nodesToSearchAgainst, querySegmentPrefixes, querySegmentPrefixStartIndex);
            }

            if (suggestions != null) {
                return this.toLookupElements(suggestions);
            }

            return null;

        } finally {
            timer.stop();
            LogUtil.debug(() -> log.debug("Search took " + timer));
        }
    }

    @Nullable
    private Set<Suggestion> doFindSuggestionsForQueryPrefix(final FileType fileType,
                                                            final Collection<MetadataSuggestionNode> nodesToSearchWithin,
                                                            final String[] querySegmentPrefixes, final int querySegmentPrefixStartIndex) {
        Set<Suggestion> suggestions = null;
        for (final MetadataSuggestionNode suggestionNode : nodesToSearchWithin) {
            final Set<Suggestion> matchedSuggestions = suggestionNode.findKeySuggestionsForQueryPrefix(this.module, fileType,
                    modifiableList(suggestionNode), 0,
                    querySegmentPrefixes, querySegmentPrefixStartIndex);

            if (matchedSuggestions != null) {
                if (suggestions == null) {
                    suggestions = new THashSet<>();
                }
                suggestions.addAll(matchedSuggestions);
            }
        }

        return suggestions;
    }

    @Nullable
    private List<LookupElement> toLookupElements(@Nullable final Set<Suggestion> suggestions) {
        if (suggestions == null) {
            return null;
        }
        return suggestions.stream()
                .map(Suggestion::newLookupElement)
                .collect(toList());
    }

    private List<MetadataContainerInfo> computeNewContainersToProcess(final OrderEnumerator orderEnumerator) {
        final List<MetadataContainerInfo> containersToProcess = new ArrayList<>();

        for (final VirtualFile metadataFileContainer : orderEnumerator.recursively().classes().getRoots()) {
            final Collection<MetadataContainerInfo> metadataContainerInfos = MetadataContainerInfo.newInstances(metadataFileContainer);

            for (final MetadataContainerInfo metadataContainerInfo : metadataContainerInfos) {
                final boolean seenBefore = this.moduleNameToSeenContainerPathToContainerInfo
                        .containsKey(metadataContainerInfo.getContainerArchiveOrFileRef());

                boolean updatedSinceLastSeen = false;
                if (seenBefore) {
                    final MetadataContainerInfo seenMetadataContainerInfo = this.moduleNameToSeenContainerPathToContainerInfo
                            .get(metadataContainerInfo.getContainerArchiveOrFileRef());
                    updatedSinceLastSeen = metadataContainerInfo.isModified(seenMetadataContainerInfo);

                    if (updatedSinceLastSeen) {
                        LogUtil.debug(() -> log.debug("Container seems to have been updated. Previous version: "
                                + seenMetadataContainerInfo + "; Newer version: " + metadataContainerInfo));
                    }
                }

                final boolean looksFresh = !seenBefore || updatedSinceLastSeen;
                final boolean processMetadata = looksFresh && metadataContainerInfo.containsMetadataFile();
                if (processMetadata) {
                    containersToProcess.add(metadataContainerInfo);
                }

                if (looksFresh) {
                    try {
                        this.moduleNameToSeenContainerPathToContainerInfo.put(metadataContainerInfo.getContainerArchiveOrFileRef(), metadataContainerInfo);
                    } catch (final IllegalArgumentException exception) {
                        if (!metadataContainerInfo.equals(this.moduleNameToSeenContainerPathToContainerInfo.get(metadataContainerInfo.getContainerArchiveOrFileRef()))) {
                            throw exception;
                        }
                    }
                }

            }
        }

        if (containersToProcess.isEmpty()) {
            LogUtil.debug(() -> log.debug("No (new)metadata files to index"));
        }
        return containersToProcess;
    }

    /**
     * Finds the containers that are not reachable from current classpath
     *
     * @param orderEnumerator classpath roots to work with
     * @return list of container paths that are no longer valid
     */
    private List<MetadataContainerInfo> computeContainersToRemove(final OrderEnumerator orderEnumerator) {
        final Set<String> newContainerPaths = stream(orderEnumerator.recursively().classes().getRoots())
                .flatMap(MetadataContainerInfo::getContainerArchiveOrFileRefs)
                .collect(toSet());

        final Set<String> knownContainerPathSet = new THashSet<>(
                this.moduleNameToSeenContainerPathToContainerInfo.keySet().stream()
                        .filter(Objects::nonNull)
                        .collect(toSet())
        );

        knownContainerPathSet.removeAll(newContainerPaths);

        return knownContainerPathSet.stream()
                .map(this.moduleNameToSeenContainerPathToContainerInfo::get)
                .collect(toList());
    }

    private void processContainers(final List<MetadataContainerInfo> toProcess, final List<MetadataContainerInfo> containersToRemove) {
        // Lets remove references to files that are no longer present in classpath
        if (CollectionUtils.isNotEmpty(containersToRemove)) {
            containersToRemove.stream()
                    .filter(Objects::nonNull)
                    .forEach(this::removeReferences);
        }

        for (final MetadataContainerInfo metadataContainerInfo : toProcess) {
            // lets remove existing references from search index, as these files are modified, so that we can rebuild index
            if (this.moduleNameToSeenContainerPathToContainerInfo.containsKey(metadataContainerInfo.getContainerArchiveOrFileRef())) {
                this.removeReferences(metadataContainerInfo);
            }

            final String metadataFilePath = metadataContainerInfo.getFileUrl();

            try (final var inputStream = metadataContainerInfo.getMetadataFile().getInputStream()) {
                final SpringConfigurationMetadata springConfigurationMetadata = JsonUtil.fromJson(inputStream, SpringConfigurationMetadata.class);

                this.buildMetadataHierarchy(metadataContainerInfo, springConfigurationMetadata);

                this.moduleNameToSeenContainerPathToContainerInfo.put(metadataContainerInfo.getContainerArchiveOrFileRef(), metadataContainerInfo);

            } catch (final IOException e) {
                log.error("Exception encountered while processing metadata file: " + metadataFilePath, e);
                this.removeReferences(metadataContainerInfo);
            }
        }
    }

    private void removeReferences(final MetadataContainerInfo metadataContainerInfo) {
        LogUtil.debug(() -> log.debug("Removing references to " + metadataContainerInfo));

        final String containerPath = metadataContainerInfo.getContainerArchiveOrFileRef();
        this.moduleNameToSeenContainerPathToContainerInfo.remove(containerPath);

        final Iterator<String> searchIndexIterator = this.moduleNameToRootSearchIndex.keySet().iterator();

        while (searchIndexIterator.hasNext()) {
            final MetadataSuggestionNode root = this.moduleNameToRootSearchIndex.get(searchIndexIterator.next());
            if (root != null) {
                final boolean removeTree = root.removeRefCascadeDown(metadataContainerInfo.getContainerArchiveOrFileRef());
                if (removeTree) {
                    searchIndexIterator.remove();
                }
            }
        }

    }

    private void buildMetadataHierarchy(final MetadataContainerInfo metadataContainerInfo,
                                        final SpringConfigurationMetadata springConfigurationMetadata) {
        LogUtil.debug(() -> log.debug("Adding container to index " + metadataContainerInfo));

        final String containerPath = metadataContainerInfo.getContainerArchiveOrFileRef();
        this.addGroupsToIndex(springConfigurationMetadata, containerPath);
        this.addPropertiesToIndex(springConfigurationMetadata, containerPath);
        this.addHintsToIndex(springConfigurationMetadata, containerPath);

        LogUtil.debug(() -> log.debug("Done adding container to index"));
    }

    private void addGroupsToIndex(final SpringConfigurationMetadata springConfigurationMetadata, final String containerArchiveOrFileRef) {
        final var springConfigurationMetadataGroups = springConfigurationMetadata.getGroups();
        if (springConfigurationMetadataGroups == null) {
            return;
        }

        springConfigurationMetadataGroups.sort(comparing(SpringConfigurationMetadataGroup::getName));

        springConfigurationMetadataGroups.forEach(springConfigurationMetadataGroup -> {
            final String[] pathSegments = toSanitizedPathSegments(springConfigurationMetadataGroup.getName());
            final String[] rawPathSegments = toRawPathSegments(springConfigurationMetadataGroup.getName());

            MetadataSuggestionNode closestMetadata = this.findDeepestMetadataMatch(this.moduleNameToRootSearchIndex, pathSegments, Boolean.FALSE);

            final int startIndex;
            if (closestMetadata == null) { // path does not have a corresponding root element
                // lets build just the root element. Rest of the path segments will be taken care of by the addChildren method
                final boolean onlyRootSegmentExists = pathSegments.length == 1;
                final MetadataNonPropertySuggestionNode newGroupSuggestionNode = MetadataNonPropertySuggestionNode
                        .newInstance(rawPathSegments[0], null, containerArchiveOrFileRef);

                if (onlyRootSegmentExists) {
                    newGroupSuggestionNode.setGroup(this.module, springConfigurationMetadataGroup);
                }

                this.moduleNameToRootSearchIndex.put(pathSegments[0], newGroupSuggestionNode);
                closestMetadata = newGroupSuggestionNode;
                // since we already handled the root level item, let addChildren start from index 1 of pathSegments
                startIndex = 1;
            } else {
                startIndex = closestMetadata.numOfHopesToRoot() + 1;
            }

            if (closestMetadata.isProperty()) {
                log.warn("Detected conflict between an existing metadata property & new group for suggestion path " +
                        closestMetadata.getPathFromRoot() + ". Ignoring new group. Existing Property belongs to (" +
                        String.join(",", closestMetadata.getBelongsTo()) + "), New Group belongs to " + containerArchiveOrFileRef);
            } else {
                // lets add container as a reference till root
                final MetadataNonPropertySuggestionNode groupSuggestionNode = (MetadataNonPropertySuggestionNode) closestMetadata;
                groupSuggestionNode.addRefCascadeTillRoot(containerArchiveOrFileRef);

                final boolean haveMoreSegmentsLeft = startIndex < rawPathSegments.length;
                if (haveMoreSegmentsLeft) {
                    groupSuggestionNode.addChildren(this.module, springConfigurationMetadataGroup, rawPathSegments, startIndex, containerArchiveOrFileRef);
                } else {
                    // Node is an intermediate node that has neither group nor property assigned to it, lets assign this group to it
                    // Can happen when `a.b.c` is already added to the metadata tree from an earlier metadata source & now we are trying to add a group for `a.b`
                    // In this e.g, startIndex would be 2. So, there is no point in adding children. We only need to update the tree appropriately
                    groupSuggestionNode.setGroup(this.module, springConfigurationMetadataGroup);
                }
            }
        });

    }

    private void addPropertiesToIndex(final SpringConfigurationMetadata springConfigurationMetadata, final String containerArchiveOrFileRef) {
        final List<SpringConfigurationMetadataProperty> properties = springConfigurationMetadata.getProperties();
        if (Objects.isNull(properties)) {
            return;
        }

        properties.sort(comparing(SpringConfigurationMetadataProperty::getName));

        properties.forEach(property -> {
            final String[] pathSegments = toSanitizedPathSegments(property.getName());
            final String[] rawPathSegments = toRawPathSegments(property.getName());
            MetadataSuggestionNode closestMetadata = this.findDeepestMetadataMatch(this.moduleNameToRootSearchIndex, pathSegments, Boolean.FALSE);

            final int startIndex;
            if (closestMetadata == null) { // path does not have a corresponding root element
                final boolean onlyRootSegmentExists = pathSegments.length == 1;
                if (onlyRootSegmentExists) {
                    closestMetadata = MetadataPropertySuggestionNode.newInstance(rawPathSegments[0], property, null, containerArchiveOrFileRef);
                } else {
                    closestMetadata = MetadataNonPropertySuggestionNode.newInstance(rawPathSegments[0], null, containerArchiveOrFileRef);
                }

                this.moduleNameToRootSearchIndex.put(pathSegments[0], closestMetadata);
                // since we already handled the root level item, let addChildren start from index 1 of pathSegments
                startIndex = 1;
            } else {
                startIndex = closestMetadata.numOfHopesToRoot() + 1;
            }

            final boolean haveMoreSegmentsLeft = startIndex < rawPathSegments.length;
            if (haveMoreSegmentsLeft) {
                if (closestMetadata.isProperty()) {
                    log.warn("Detected conflict between a new group & existing property for suggestion path " + closestMetadata.getPathFromRoot() +
                            ". Ignoring property. Existing non property node belongs to (" + String.join(",", closestMetadata.getBelongsTo()) +
                            "), New property belongs to " + containerArchiveOrFileRef);
                } else {
                    ((MetadataNonPropertySuggestionNode) closestMetadata).addChildren(property, rawPathSegments, startIndex, containerArchiveOrFileRef);
                }

            } else {
                if (closestMetadata.isProperty()) {
                    closestMetadata.addRefCascadeTillRoot(containerArchiveOrFileRef);
                    log.debug("Detected a duplicate metadata property for suggestion path " + closestMetadata.getPathFromRoot() +
                            ". Ignoring property. Existing property belongs to (" + String.join(",", closestMetadata.getBelongsTo()) +
                            "), New property belongs to " + containerArchiveOrFileRef);
                } else {
                    log.warn("Detected conflict between a new metadata property & existing non property node for suggestion path " +
                            closestMetadata.getPathFromRoot() + ". Ignoring property. Existing non property node belongs to (" +
                            String.join(",", closestMetadata.getBelongsTo()) + "), New property belongs to " + containerArchiveOrFileRef);
                }
            }

        });
    }

    private void addHintsToIndex(final SpringConfigurationMetadata springConfigurationMetadata, final String containerPath) {
        final List<SpringConfigurationMetadataHint> hints = springConfigurationMetadata.getHints();
        if (hints == null) {
            return;
        }

        hints.sort(comparing(SpringConfigurationMetadataHint::getName));

        hints.forEach(hint -> {
            final String[] pathSegments = toSanitizedPathSegments(hint.getExpectedPropertyName());
            final MetadataSuggestionNode closestMetadata = this.findDeepestMetadataMatch(this.moduleNameToRootSearchIndex, pathSegments, Boolean.TRUE);

            if (Objects.nonNull(closestMetadata)) {
                if (closestMetadata.isProperty()) {
                    final MetadataPropertySuggestionNode propertySuggestionNode = (MetadataPropertySuggestionNode) closestMetadata;
                    if (hint.representsValueOfMap()) {
                        propertySuggestionNode.getProperty().setValueHint(hint);
                    } else {
                        propertySuggestionNode.getProperty().setGenericOrKeyHint(hint);
                    }
                } else {
                    log.warn("Unexpected hint " + hint.getName() + " is assigned to  group " + closestMetadata.getPathFromRoot() +
                            " found. Hints can be only assigned to property. Ignoring the hint completely.Existing group belongs to (" +
                            String.join(",", closestMetadata.getBelongsTo()) + "), New hint belongs " + containerPath);
                }
            }
        });

    }

    private MetadataSuggestionNode findDeepestMetadataMatch(final Map<String, MetadataSuggestionNode> roots,
                                                            final String[] pathSegments,
                                                            final boolean matchAllSegments) {
        final MetadataSuggestionNode closestMatchedRoot = roots.get(pathSegments[0]);
        return Objects.isNull(closestMatchedRoot) ? null : closestMatchedRoot.findDeepestMetadataNode(pathSegments, 1, matchAllSegments);
    }

    @SuppressWarnings("unused")
    private String toTree() {
        final var builder = new StringBuilder();

        this.moduleNameToRootSearchIndex.forEach((key, value) ->
                builder.append("Module: ").append(key).append(StringUtils.LF)
                        .append(value.toTree().trim()
                                .replaceFirst("^", "  ")
                                .replace(StringUtils.LF, "\n  "))
                        .append(StringUtils.LF));

        return builder.toString();
    }

}
