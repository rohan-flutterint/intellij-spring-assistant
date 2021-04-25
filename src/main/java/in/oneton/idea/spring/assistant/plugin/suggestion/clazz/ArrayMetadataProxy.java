package in.oneton.idea.spring.assistant.plugin.suggestion.clazz;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiType;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType;
import in.oneton.idea.spring.assistant.plugin.suggestion.completion.FileType;
import in.oneton.idea.spring.assistant.plugin.suggestion.completion.SuggestionDocumentationHelper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.intellij.util.containers.ContainerUtil.isEmpty;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.newListWithMembers;
import static in.oneton.idea.spring.assistant.plugin.suggestion.clazz.ClassSuggestionNodeFactory.newMetadataProxy;
import static java.util.stream.Collectors.toCollection;

public class ArrayMetadataProxy implements MetadataProxy {

    @NotNull
    private final PsiArrayType type;
    @Nullable
    private final MetadataProxy delegate;

    ArrayMetadataProxy(final Module module, @NotNull final PsiArrayType type) {
        this.type = type;
        this.delegate = newMetadataProxy(module, type.getComponentType());
    }

    @Nullable
    @Override
    public SuggestionDocumentationHelper findDirectChild(final Module module, final String pathSegment) {
        return this.doWithDelegateAndReturn(delegate -> delegate.findDirectChild(module, pathSegment), null);
    }

    @Nullable
    @Override
    public Collection<? extends SuggestionDocumentationHelper> findDirectChildrenForQueryPrefix(
            final Module module, final String querySegmentPrefix) {
        return this.findDirectChildrenForQueryPrefix(module, querySegmentPrefix, null);
    }

    @Nullable
    @Override
    public Collection<? extends SuggestionDocumentationHelper> findDirectChildrenForQueryPrefix(
            final Module module, final String querySegmentPrefix, @Nullable final Set<String> siblingsToExclude) {
        // TODO: Should each element be wrapped inside Iterale Suggestion element?
        return this.doWithDelegateAndReturn(delegate -> delegate
                .findDirectChildrenForQueryPrefix(module, querySegmentPrefix, siblingsToExclude), null);
    }

    @Nullable
    @Override
    public List<SuggestionNode> findDeepestSuggestionNode(final Module module,
                                                          final List<SuggestionNode> matchesRootTillParentNode, final String[] pathSegments,
                                                          final int pathSegmentStartIndex) {
        return this.doWithDelegateAndReturn(delegate -> {
            final String pathSegment = pathSegments[pathSegmentStartIndex];
            final SuggestionDocumentationHelper directChildKeyMatch =
                    delegate.findDirectChild(module, pathSegment);
            if (directChildKeyMatch != null) {
                // TODO: Need to identify a better mechanism than this dirty way. Probably use ClassSuggestionNode as return type for findDirectChildrenForQueryPrefix
                // since we are in an iterable(multiple values), keys would be requested, only if the object we are referring is not a leaf => GenericClassWrapper
                assert directChildKeyMatch instanceof SuggestionNode;
                matchesRootTillParentNode
                        .add(new IterableKeySuggestionNode((SuggestionNode) directChildKeyMatch));
                final boolean lastPathSegment = pathSegmentStartIndex == pathSegments.length - 1;
                if (lastPathSegment) {
                    return matchesRootTillParentNode;
                } else {
                    return delegate.findDeepestSuggestionNode(module, matchesRootTillParentNode, pathSegments,
                            pathSegmentStartIndex);
                }
            }
            return null;
        }, null);
    }

    @Nullable
    @Override
    public SortedSet<Suggestion> findKeySuggestionsForQueryPrefix(final Module module, final FileType fileType,
                                                                  final List<SuggestionNode> matchesRootTillParentNode, final int numOfAncestors,
                                                                  final String[] querySegmentPrefixes, final int querySegmentPrefixStartIndex) {
        return this.findKeySuggestionsForQueryPrefix(module, fileType, matchesRootTillParentNode,
                numOfAncestors, querySegmentPrefixes, querySegmentPrefixStartIndex, null);
    }

    @Nullable
    @Override
    public SortedSet<Suggestion> findKeySuggestionsForQueryPrefix(final Module module, final FileType fileType,
                                                                  final List<SuggestionNode> matchesRootTillParentNode, final int numOfAncestors,
                                                                  final String[] querySegmentPrefixes, final int querySegmentPrefixStartIndex,
                                                                  @Nullable final Set<String> siblingsToExclude) {
        return this.doWithDelegateAndReturn(delegate -> {
            final String querySegmentPrefix = querySegmentPrefixes[querySegmentPrefixStartIndex];
            final Collection<? extends SuggestionDocumentationHelper> matches =
                    delegate.findDirectChildrenForQueryPrefix(module, querySegmentPrefix, siblingsToExclude);
            if (!isEmpty(matches)) {
                return matches.stream().map(helper -> {
                    // TODO: Need to identify a better mechanism than this dirty way. Probably use ClassSuggestionNode as return type for findDirectChildrenForQueryPrefix
                    // since we are in an iterable(multiple values), keys would be requested, only if the object we are referring is not a leaf => GenericClassWrapper
                    assert helper instanceof SuggestionNode;
                    final List<SuggestionNode> rootTillMe = newListWithMembers(matchesRootTillParentNode,
                            new IterableKeySuggestionNode((SuggestionNode) helper));
                    return helper.buildSuggestionForKey(module, fileType, rootTillMe, numOfAncestors);
                }).collect(toCollection(TreeSet::new));
            }
            return null;
        }, null);
    }

    @Nullable
    @Override
    public SortedSet<Suggestion> findValueSuggestionsForPrefix(final Module module, final FileType fileType,
                                                               final List<SuggestionNode> matchesRootTillMe, final String prefix) {
        return this.findValueSuggestionsForPrefix(module, fileType, matchesRootTillMe, prefix, null);
    }

    @Nullable
    @Override
    public SortedSet<Suggestion> findValueSuggestionsForPrefix(final Module module, final FileType fileType,
                                                               final List<SuggestionNode> matchesRootTillMe, final String prefix,
                                                               @Nullable final Set<String> siblingsToExclude) {
        return this.doWithDelegateAndReturn(delegate -> delegate
                .findValueSuggestionsForPrefix(module, fileType, matchesRootTillMe, prefix,
                        siblingsToExclude), null);
    }

    @Nullable
    @Override
    public String getDocumentationForValue(final Module module, final String nodeNavigationPathDotDelimited,
                                           final String originalValue) {
        return this.doWithDelegateAndReturn(delegate -> delegate
                .getDocumentationForValue(module, nodeNavigationPathDotDelimited, originalValue), null);
    }

    @Override
    public boolean isLeaf(final Module module) {
        return this.doWithDelegateAndReturn(delegate -> delegate.isLeaf(module), true);
    }

    @NotNull
    @Override
    public SuggestionNodeType getSuggestionNodeType(final Module module) {
        return SuggestionNodeType.ARRAY;
    }

    @Nullable
    @Override
    public PsiType getPsiType(final Module module) {
        return this.type;
    }

    @Override
    public boolean targetRepresentsArray() {
        return true;
    }

    @Override
    public boolean targetClassRepresentsIterable(final Module module) {
        return false;
    }

    private <T> T doWithDelegateAndReturn(
            final MetadataProxyInvokerWithReturnValue<T> targetInvokerWithReturnValue, final T defaultReturnValue) {
        if (this.delegate != null) {
            return targetInvokerWithReturnValue.invoke(this.delegate);
        }
        return defaultReturnValue;
    }

    private void doWithDelegate(final MetadataProxyInvoker targetInvoker) {
        if (this.delegate != null) {
            targetInvoker.invoke(this.delegate);
        }
    }

}
