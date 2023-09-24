/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
