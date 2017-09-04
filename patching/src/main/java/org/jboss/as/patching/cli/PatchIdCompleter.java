/*
Copyright 2017 Red Hat, Inc.

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
package org.jboss.as.patching.cli;

import java.util.ArrayList;
import java.util.List;
import org.aesh.command.CommandException;
import org.aesh.command.completer.OptionCompleter;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.PatchingException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
/**
 *
 * @author jdenise@redhat.com
 */
public class PatchIdCompleter implements OptionCompleter<CLICompleterInvocation> {

    @Override
    public void complete(CLICompleterInvocation completerInvocation) {
        AbstractDistributionCommand cmd = (AbstractDistributionCommand) completerInvocation.getCommand();
        try {
            List<ModelNode> patches = PatchRollbackActivator.getPatches(completerInvocation.getCommandContext(), cmd, cmd.getPatchStream(), cmd.getHost());
            List<String> names = new ArrayList<>();
            for (ModelNode mn : patches) {
                if (mn.hasDefined(Constants.PATCH_ID)) {
                    names.add(mn.get(Constants.PATCH_ID).asString());
                }
            }
            String buffer = completerInvocation.getGivenCompleteValue();
            if (buffer == null || buffer.isEmpty()) {
                completerInvocation.addAllCompleterValues(names);
            } else {
                for (String n : names) {
                    if (n.startsWith(buffer)) {
                        completerInvocation.addCompleterValue(n);
                        completerInvocation.setOffset(buffer.length());
                    }
                }
            }
        } catch (PatchingException | CommandException ex) {
            // OK, will not complete.
            return;
        }

    }
}
