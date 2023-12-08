/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineCompleter;

/**
 *
 * @author Alexey Loubyansky
 */
public class SimpleTabCompleter implements CommandLineCompleter {

    public static final SimpleTabCompleter BOOLEAN = new SimpleTabCompleter(new String[]{"false", "true"});

    private final List<String> all;

    public SimpleTabCompleter(String[] candidates) {
        all = Arrays.asList(checkNotNullParam("candidates", candidates));
        Collections.sort(all);
    }

    public SimpleTabCompleter(Collection<? extends Object> candidates) {
        this.all = candidates.stream().map(Object::toString).collect(Collectors.toList());
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandLineCompleter#complete(org.jboss.as.cli.CommandContext, java.lang.String, int, java.util.List)
     */
    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {

        int nextCharIndex = 0;
        while(nextCharIndex < buffer.length()) {
            if(!Character.isWhitespace(buffer.charAt(nextCharIndex))) {
                break;
            }
            ++nextCharIndex;
        }

        if(nextCharIndex == buffer.length()) {
            candidates.addAll(all);
            return nextCharIndex;
        }

        String[] split = buffer.split("\\s+");
        int result;
        final String chunk;
        if(Character.isWhitespace(buffer.charAt(buffer.length() - 1))) {
            chunk = null;
            result = buffer.length();
        } else {
            chunk = split[split.length - 1];
            result = buffer.length() - 1;
            while(result >= 0 && !Character.isWhitespace(buffer.charAt(result))) {
                --result;
            }
            ++result;
        }

        final List<String> remainingArgs = new ArrayList<String>(all);
        int maxI = chunk == null ? split.length : split.length - 1;
        for(int i = 0; i < maxI; ++i) {
            String arg = split[i];
            int equalsIndex = arg.indexOf('=');
            if(equalsIndex >= 0) {
                arg = arg.substring(0, equalsIndex + 1);
            }
            remainingArgs.remove(arg);
        }

        if (chunk == null) {
            candidates.addAll(remainingArgs);
        } else {
            for(String name : remainingArgs) {
                if(name.startsWith(chunk)) {
                    candidates.add(name);
                }
            }
        }
        return result;
    }
}
