/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aesh.command.completer.OptionCompleter;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;

/**
 * Base class for completion of discrete values.
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractCompleter implements OptionCompleter<CLICompleterInvocation> {

    @Override
    public void complete(CLICompleterInvocation completerInvocation) {
        if (completerInvocation.getCommandContext().getModelControllerClient() != null) {
            List<String> items = getItems(completerInvocation);
            if (items != null && !items.isEmpty()) {
                List<String> candidates = new ArrayList<>();
                String opBuffer = completerInvocation.getGivenCompleteValue();
                if (opBuffer.isEmpty()) {
                    candidates.addAll(items);
                } else {
                    for (String name : items) {
                        if (name.startsWith(opBuffer)) {
                            candidates.add(name);
                        }
                    }
                    Collections.sort(candidates);
                }
                completerInvocation.addAllCompleterValues(candidates);
            }
        }
    }

    protected abstract List<String> getItems(CLICompleterInvocation completerInvocation);

}
