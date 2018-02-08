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
