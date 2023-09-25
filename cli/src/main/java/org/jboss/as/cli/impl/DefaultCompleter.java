/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineCompleter;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultCompleter implements CommandLineCompleter {

    public interface CandidatesProvider {
        Collection<String> getAllCandidates(CommandContext ctx);
    }

    private final CandidatesProvider candidatesProvider;

    public DefaultCompleter(CandidatesProvider candidatesProvider) {
        this.candidatesProvider = checkNotNullParam("candidatesProvider", candidatesProvider);
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandLineCompleter#complete(org.jboss.as.cli.CommandContext, java.lang.String, int, java.util.List)
     */
    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {

        int nextCharIndex = 0;
        while (nextCharIndex < buffer.length()) {
            if (!Character.isWhitespace(buffer.charAt(nextCharIndex))) {
                break;
            }
            ++nextCharIndex;
        }

        final Collection<String> all = candidatesProvider.getAllCandidates(ctx);
        if (all.isEmpty()) {
            return -1;
        }

        String opBuffer = buffer.substring(nextCharIndex).trim();
        if (opBuffer.isEmpty()) {
            candidates.addAll(all);
        } else {
            for (String name : all) {
                if (name.startsWith(opBuffer)) {
                    candidates.add(name);
                }
            }
            Collections.sort(candidates);
        }
        return nextCharIndex;
    }
}
