/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.aesh.command.completer.OptionCompleter;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;

/**
 * Base class to complete values separated by ',' character.
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractCommaCompleter implements OptionCompleter<CLICompleterInvocation> {

    protected abstract List<String> getItems(CLICompleterInvocation completerInvocation);

    @Override
    public void complete(CLICompleterInvocation completerInvocation) {
        if (completerInvocation.getCommandContext().getModelControllerClient() != null) {
            completerInvocation.setAppendSpace(false);
            List<String> items = getItems(completerInvocation);
            if (items != null && !items.isEmpty()) {
                List<String> candidates = new ArrayList<>();
                candidates.addAll(items);
                String buffer = completerInvocation.getGivenCompleteValue();
                if (!buffer.isEmpty()) {
                    final String[] specified = buffer.split(",+");
                    candidates.removeAll(Arrays.asList(specified));
                    if (buffer.charAt(buffer.length() - 1) == ',') {
                        completerInvocation.addAllCompleterValues(candidates);
                        completerInvocation.setOffset(0);
                        return;
                    }
                    final String chunk = specified[specified.length - 1];
                    final Iterator<String> iterator = candidates.iterator();
                    List<String> remaining = new ArrayList<>();
                    while (iterator.hasNext()) {
                        String i = iterator.next();
                        if (!i.startsWith(chunk)) {
                            remaining.add(i);
                            iterator.remove();
                        }
                    }
                    if (candidates.isEmpty() && !remaining.isEmpty()) {
                        candidates.add(",");
                        completerInvocation.setOffset(0);
                    } else {
                        completerInvocation.setOffset(chunk.length());
                    }
                }
                completerInvocation.addAllCompleterValues(candidates);
            }
        }
    }
}
