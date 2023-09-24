/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineCompleter;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class CommaSeparatedCompleter implements CommandLineCompleter {

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandLineCompleter#complete(org.jboss.as.cli.CommandContext, java.lang.String, int, java.util.List)
     */
    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {

        final Collection<String> all = getAllCandidates(ctx);
        if (all.isEmpty()) {
            return -1;
        }

        candidates.addAll(all);
        if(buffer.isEmpty()) {
            return 0;
        }
        final String[] specified = buffer.split(",+");
        candidates.removeAll(Arrays.asList(specified));
        if(buffer.charAt(buffer.length() - 1) == ',') {
            return buffer.length();
        }
        final String chunk = specified[specified.length - 1];
        final Iterator<String> iterator = candidates.iterator();
        while(iterator.hasNext()) {
            if(!iterator.next().startsWith(chunk)) {
                iterator.remove();
            }
        }
        return buffer.length() - chunk.length();
    }

    protected abstract Collection<String> getAllCandidates(CommandContext ctx);
}
