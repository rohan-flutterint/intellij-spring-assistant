package in.oneton.idea.spring.assistant.plugin.suggestion.completion;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.Constants;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.lang.java.JavaLanguage.INSTANCE;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.truncateIdeaDummyIdentifier;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public class YamlDocumentationProvider extends AbstractDocumentationProvider {
    @Override
    public String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
        if (element instanceof DocumentationProxyElement) {
            final DocumentationProxyElement proxyElement = (DocumentationProxyElement) element;
            final DocumentationProvider target = proxyElement.target;

            // Intermediate nodes will not have documentation
            if (target != null && target.supportsDocumentation()) {
                final Module module = findModule(element);
                if (proxyElement.requestedForTargetValue) {
                    return target
                            .getDocumentationForValue(module, proxyElement.nodeNavigationPathDotDelimited,
                                    proxyElement.value);
                } else {
                    return target.getDocumentationForKey(module, proxyElement.nodeNavigationPathDotDelimited);
                }
            }
        }
        return super.generateDoc(element, originalElement);
    }

    /*
     * This will called if the user tries to lookup documentation for the choices being shown (ctrl+q within suggestion dialog)
     */
    @Override
    public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object,
                                                           @Nullable final PsiElement element) {
        if (object instanceof Suggestion) {
            final Suggestion suggestion = (Suggestion) object;
            return new DocumentationProxyElement(psiManager, INSTANCE, suggestion.getFullPath(),
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
            boolean requestedForTargetValue = false;

            final Module module = findModule(element);
            final var suggestionService = SuggestionService.getInstance(module);

            List<String> ancestralKeys = null;
            final PsiElement elementContext = element.getContext();
            PsiElement context = elementContext;
            do {
                if (context instanceof YAMLKeyValue) {
                    if (ancestralKeys == null) {
                        ancestralKeys = new ArrayList<>();
                    }
                    ancestralKeys.add(0, truncateIdeaDummyIdentifier(((YAMLKeyValue) context).getKeyText()));
                }
                context = requireNonNull(context).getParent();
            } while (context != null);

            String value = null;
            if (elementContext instanceof YAMLKeyValue) {
                value = truncateIdeaDummyIdentifier(((YAMLKeyValue) elementContext).getKeyText());
            } else if (elementContext instanceof YAMLPlainTextImpl) {
                value = truncateIdeaDummyIdentifier(element.getText());
                requestedForTargetValue = true;
            }

            if (ancestralKeys != null) {
                matchedNodesFromRootTillLeaf = suggestionService.findMatchedNodesRootTillEnd(ancestralKeys);
                if (matchedNodesFromRootTillLeaf != null) {
                    final SuggestionNode target = matchedNodesFromRootTillLeaf.get(matchedNodesFromRootTillLeaf.size() - 1);
                    final String targetNavigationPathDotDelimited = matchedNodesFromRootTillLeaf.stream()
                            .map(v -> v.getNameForDocumentation(module))
                            .collect(joining(Constants.PROP_DOT));

                    return new DocumentationProxyElement(file.getManager(), file.getLanguage(),
                            targetNavigationPathDotDelimited, target, requestedForTargetValue, value);
                }
            }
        }
        return super.getCustomDocumentationElement(editor, file, element, targetOffset);
    }

    @ToString(of = "nodeNavigationPathDotDelimited")
    private static class DocumentationProxyElement extends LightElement {
        private final DocumentationProvider target;
        private final boolean requestedForTargetValue;
        @Nullable
        private final String value;
        private final String nodeNavigationPathDotDelimited;

        DocumentationProxyElement(@NotNull final PsiManager manager, @NotNull final Language language,
                                  final String nodeNavigationPathDotDelimited, @NotNull final DocumentationProvider target,
                                  final boolean requestedForTargetValue, @Nullable final String value) {
            super(manager, language);
            this.nodeNavigationPathDotDelimited = nodeNavigationPathDotDelimited;
            this.target = target;
            this.requestedForTargetValue = requestedForTargetValue;
            this.value = value;
        }

        @Override
        public String getText() {
            return this.nodeNavigationPathDotDelimited;
        }
    }

}
