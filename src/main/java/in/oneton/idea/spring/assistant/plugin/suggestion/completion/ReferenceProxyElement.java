package in.oneton.idea.spring.assistant.plugin.suggestion.completion;

import com.intellij.lang.Language;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author zhoumengjie02, darren
 */
@ToString(onlyExplicitlyIncluded = true)
public class ReferenceProxyElement extends LightElement {

    @Getter
    private final DocumentationProvider target;

    @Getter
    private final boolean requestedForTargetValue;

    @Nullable
    @Getter
    private final String value;

    @Getter
    @ToString.Include
    private final String nodeNavigationPathDotDelimited;

    ReferenceProxyElement(@NotNull final PsiManager manager, @NotNull final Language language,
                          final String nodeNavigationPathDotDelimited, @NotNull final DocumentationProvider target,
                          @Nullable final String value) {
        this(manager, language, nodeNavigationPathDotDelimited, target, false, value);
    }
    
    ReferenceProxyElement(@NotNull final PsiManager manager, @NotNull final Language language,
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