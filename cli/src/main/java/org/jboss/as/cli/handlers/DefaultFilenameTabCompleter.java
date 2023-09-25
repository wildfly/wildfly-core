/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.EscapeSelector;
import org.jboss.as.cli.Util;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultFilenameTabCompleter extends FilenameTabCompleter {

    private static final EscapeSelector ESCAPE_SELECTOR = new EscapeSelector() {
       @Override
       public boolean isEscape(char ch) {
           return ch == '\\' || ch == ' ' || ch == '"';
       }
    };

    private static final EscapeSelector QUOTES_ONLY_ESCAPE_SELECTOR = new EscapeSelector() {
        @Override
        public boolean isEscape(char ch) {
            return ch == '"';
        }
    };

   public DefaultFilenameTabCompleter(CommandContext ctx) {
       super(ctx);
   }

    /**
     * The only supported syntax at command execution is fully quoted, e.g.:
     * "/My Files\..." or not quoted at all. Completion supports only these 2
     * syntaxes.
     */
    @Override
    void completeCandidates(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
        boolean quoted = buffer.startsWith("\"");
        if (candidates.size() == 1) {
            // Escaping must occur in all cases.
            // if quoted, only " will be escaped.
            EscapeSelector escSelector = quoted
                    ? QUOTES_ONLY_ESCAPE_SELECTOR : ESCAPE_SELECTOR;
            candidates.set(0, Util.escapeString(candidates.get(0), escSelector));
        }
    }
}