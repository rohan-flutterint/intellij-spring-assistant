package in.oneton.idea.spring.assistant.plugin.suggestion.completion;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.Constants;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.lang.java.JavaLanguage.INSTANCE;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.truncateIdeaDummyIdentifier;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public class YamlDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
        if (element instanceof ReferenceProxyElement) {
            final ReferenceProxyElement proxyElement = (ReferenceProxyElement) element;
            final DocumentationProvider target = proxyElement.getTarget();
            // Intermediate nodes will not have documentation
            if (target != null && target.supportsDocumentation()) {
                final Module module = findModule(element);
                if (proxyElement.isRequestedForTargetValue()) {
                    return target.getDocumentationForValue(module, proxyElement.getNodeNavigationPathDotDelimited(), proxyElement.getValue());
                } else {
                    return target.getDocumentationForKey(module, proxyElement.getNodeNavigationPathDotDelimited());
                }
            }
        }

        return super.generateDoc(element, originalElement);
    }

    /*
     * This will called if the user tries to lookup documentation for the choices being shown (ctrl+q within suggestion dialog)
     */
    @Override
    public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, @Nullable final PsiElement element) {
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
            final Module module = findModule(element);
            if (Objects.isNull(module)) {
                return super.getCustomDocumentationElement(editor, file, element, targetOffset);
            }

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

            boolean requestedForTargetValue = false;
            String value = null;
            if (elementContext instanceof YAMLKeyValue) {
                value = truncateIdeaDummyIdentifier(((YAMLKeyValue) elementContext).getKeyText());
            } else if (elementContext instanceof YAMLPlainTextImpl) {
                value = truncateIdeaDummyIdentifier(element.getText());
                requestedForTargetValue = true;
            }

            if (ancestralKeys != null) {
                final var suggestionService = SuggestionService.getInstance(module);
                final List<SuggestionNode> matchedNodesFromRootTillLeaf = suggestionService.findMatchedNodesRootTillEnd(ancestralKeys);

                if (matchedNodesFromRootTillLeaf != null) {
                    final SuggestionNode target = matchedNodesFromRootTillLeaf.get(matchedNodesFromRootTillLeaf.size() - 1);
                    final String targetNavigationPathDotDelimited = matchedNodesFromRootTillLeaf.stream()
                            .map(v -> v.getNameForDocumentation(module))
                            .collect(joining(Constants.PROP_DOT));

                    return new ReferenceProxyElement(file.getManager(), file.getLanguage(),
                            targetNavigationPathDotDelimited, target, requestedForTargetValue, value);
                }
            }
        }

        return super.getCustomDocumentationElement(editor, file, element, targetOffset);
    }

}
