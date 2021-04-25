package in.oneton.idea.spring.assistant.plugin.suggestion.completion;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import in.oneton.idea.spring.assistant.plugin.misc.GenericUtil;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.truncateIdeaDummyIdentifier;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static java.util.stream.Collectors.joining;

/**
 * @author zhoumengjie02, darren
 */
public class PropertiesReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {

        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PropertyKeyImpl.class), new PsiReferenceProvider() {
            @NotNull
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {

                final PropertyKeyImpl property = (PropertyKeyImpl) element;

                final String text = property.getText();

                final var range = new TextRange(0, text.length());

                return new PsiReference[]{new PropertiesReference(element, range)};
            }
        });

    }


    static class PropertiesReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

        public PropertiesReference(final PsiElement element, final TextRange rangeInElement) {
            super(element, rangeInElement);
        }

        @NotNull
        @Override
        public ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {

            final var project = this.myElement.getProject();

            final PsiFile file = this.myElement.getContainingFile();

            final var service = project.getService(SuggestionService.class);

            final String value = this.myElement.getText();

            final Module module = findModule(this.myElement);

            final List<String> ancestralKey = GenericUtil.getAncestralKey(value);

            if (CollectionUtils.isEmpty(ancestralKey)) {
                return new ResolveResult[0];
            }

            final List<SuggestionNode> matchedNodesFromRootTillLeaf =
                    service.findMatchedNodesRootTillEnd(module, ancestralKey);

            if (matchedNodesFromRootTillLeaf != null) {

                final SuggestionNode target = matchedNodesFromRootTillLeaf.get(matchedNodesFromRootTillLeaf.size() - 1);

                final String targetNavigationPathDotDelimited =
                        matchedNodesFromRootTillLeaf.stream().map(v -> v.getNameForDocumentation(module))
                                .collect(joining("."));

                final ReferenceProxyElement element = new ReferenceProxyElement(file.getManager(), file.getLanguage(),
                        targetNavigationPathDotDelimited, target, value);

                return new ResolveResult[]{new PsiElementResolveResult(element)};
            }

            return new ResolveResult[0];
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            final ResolveResult[] resolveResults = this.multiResolve(false);
            return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
        }

        @NotNull
        @Override
        public Object @NotNull [] getVariants() {

            final var project = this.myElement.getProject();

            final var service = project.getService(SuggestionService.class);

            final Module module = findModule(this.myElement);

            final String origin = truncateIdeaDummyIdentifier(this.myElement);

            final int pos = origin.lastIndexOf(".");

            String queryWithDotDelimitedPrefixes = origin;

            if (pos != -1) {
                if (pos == origin.length() - 1) {
                    queryWithDotDelimitedPrefixes = "";
                } else {
                    queryWithDotDelimitedPrefixes = origin.substring(pos + 1);
                }
            }

            final List<LookupElementBuilder> suggestions =
                    service.findSuggestionsForQueryPrefix(module,
                            FileType.PROPERTIES, this.myElement,
                            GenericUtil.getAncestralKey(origin), queryWithDotDelimitedPrefixes,
                            null);

            if (suggestions != null) {
                return suggestions.toArray();
            }
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }


    }
}