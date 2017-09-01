/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.patching.cli;

import java.util.ArrayList;
import java.util.List;
import org.aesh.command.CommandException;
import org.aesh.command.completer.OptionCompleter;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.tool.PatchOperationBuilder;
import org.jboss.as.patching.tool.PatchOperationTarget;
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
            if (completerInvocation.getCommandContext().isDomainMode() && cmd.getHost() == null) {
                return;
            }
            final PatchOperationTarget target = cmd.createPatchOperationTarget(completerInvocation.getCommandContext());
            PatchOperationBuilder builder = PatchOperationBuilder.Factory.info(cmd.getPatchStream());
            ModelNode response = builder.execute(target);
            final ModelNode result = response.get(ModelDescriptionConstants.RESULT);
            if (!result.isDefined()) {
                return;
            }
            List<ModelNode> patches = result.get(Constants.PATCHES).asList();
            List<String> names = new ArrayList<>();
            for (ModelNode p : patches) {
                names.add(p.asString());
            }
            String buffer = completerInvocation.getGivenCompleteValue();
            if (buffer == null || buffer.isEmpty()) {
                completerInvocation.addAllCompleterValues(names);
            } else {
                for (String n : names) {
                    if (n.startsWith(buffer) && !n.equals(buffer)) {
                        completerInvocation.addCompleterValue(n);
                        completerInvocation.setOffset(buffer.length());
                    }
                }
            }
        } catch (CommandException | PatchingException ex) {
            // OK, will not complete.
            return;
        }

    }
}
