/*
Copyright 2018 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
