package com.github.eltonsandre.plugin.idea.spring.assistant.common;

import com.intellij.openapi.diagnostic.Logger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogUtil {

    private static final Logger log = Logger.getInstance(LogUtil.class);

    /**
     * Debug logging can be enabled by adding fully classified class name/package name with # prefix
     * For eg., to enable debug logging, go `Help >> Diaginostic Tools >> Debug log settings`
     * & type `#in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionServiceImpl`
     *
     * @param doWhenDebug code to execute when debug is enabled
     */
    public static void debug(final Runnable doWhenDebug) {
        if (log.isDebugEnabled()) {
            doWhenDebug.run();
        }
    }

}
