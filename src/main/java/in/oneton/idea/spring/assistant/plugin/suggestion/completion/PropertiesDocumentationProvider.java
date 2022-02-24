package in.oneton.idea.spring.assistant.plugin.suggestion.completion;


import com.github.eltonsandre.plugin.idea.spring.assistant.common.Constants;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import in.oneton.idea.spring.assistant.plugin.misc.GenericUtil;
import in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.lang.java.JavaLanguage.INSTANCE;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static java.util.stream.Collectors.joining;

public class PropertiesDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
        if (element instanceof ReferenceProxyElement) {
            final ReferenceProxyElement proxyElement = (ReferenceProxyElement) element;
            final DocumentationProvider target = proxyElement.getTarget();

            if (target != null && target.supportsDocumentation()) {
                final Module module = PsiCustomUtil.findModule(element);
                if (proxyElement.isRequestedForTargetValue()) {
                    return target.getDocumentationForValue(module, proxyElement.getNodeNavigationPathDotDelimited(), proxyElement.getValue());
                } else {
                    return target.getDocumentationForKey(module, proxyElement.getNodeNavigationPathDotDelimited());
                }
            }
        }

        return super.generateDoc(element, originalElement);
    }

    @Override
    public String getQuickNavigateInfo(final PsiElement element, @Nullable final PsiElement originalElement) {
        if (element instanceof ReferenceProxyElement) {
            final ReferenceProxyElement proxyElement = (ReferenceProxyElement) element;
            final SuggestionNode target = (SuggestionNode) proxyElement.getTarget();
            final boolean requestedForTargetValue = proxyElement.isRequestedForTargetValue();

            // Only group & leaf are expected to have documentation
            if (target != null && target.supportsDocumentation()) {
                final Module module = PsiCustomUtil.findModule(element);

                if (requestedForTargetValue) {
                    return target.getDocumentationForValue(module, proxyElement.getNodeNavigationPathDotDelimited(), proxyElement.getValue());
                } else {
                    return target.getDocumentationForKey(module, proxyElement.getNodeNavigationPathDotDelimited());
                }
            }
        }

        return super.getQuickNavigateInfo(element, originalElement);
    }

    @Override
    public @Nullable
    @Nls
    String generateHoverDoc(@NotNull final PsiElement element, @Nullable final PsiElement originalElement) {
        return super.generateHoverDoc(element, originalElement);
    }

    @Override
    public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object,
                                                           @Nullable final PsiElement element) {
        if (object instanceof Suggestion) {
            final Suggestion suggestion = (Suggestion) object;
            return new ReferenceProxyElement(psiManager, INSTANCE, suggestion.getFullPath(),
                    suggestion.getMatchesTopFirst().get(suggestion.getMatchesTopFirst().size() - 1),
                    suggestion.isForValue(), suggestion.getSuggestionToDisplay());
        }
        return super.getDocumentationElementForLookupItem(psiManager, object, element);
    }

    @Nullable
    @Override
    public PsiElement getCustomDocumentationElement(@NotNull final Editor editor, @NotNull final PsiFile file,
                                                    @Nullable final PsiElement element, final int targetOffset) {
        if (element != null) {
            final List<SuggestionNode> matchedNodesFromRootTillLeaf;
            final boolean requestedForTargetValue = false;

            final Module module = findModule(element);
            if (Objects.isNull(module)) {
                return super.getCustomDocumentationElement(editor, file, element, targetOffset);
            }
            final var suggestionService = SuggestionService.getInstance(module);

            final String value = element.getText();
            final List<String> ancestralKeys = GenericUtil.getKey(value);

            matchedNodesFromRootTillLeaf = suggestionService.findMatchedNodesRootTillEnd(ancestralKeys);

            if (Objects.nonNull(matchedNodesFromRootTillLeaf)) {
                final SuggestionNode target = matchedNodesFromRootTillLeaf.get(matchedNodesFromRootTillLeaf.size() - 1);
                final String targetNavigationPathDotDelimited = matchedNodesFromRootTillLeaf.stream()
                        .map(v -> v.getNameForDocumentation(module))
                        .collect(joining(Constants.PROP_DOT));

                return new ReferenceProxyElement(file.getManager(), file.getLanguage(), targetNavigationPathDotDelimited,
                        target, requestedForTargetValue, value);
            }
        }

        return super.getCustomDocumentationElement(editor, file, element, targetOffset);
    }

}
