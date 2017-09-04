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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.aesh.command.CommandException;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.tool.PatchOperationBuilder;
import org.jboss.as.patching.tool.PatchOperationTarget;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.activator.AbstractCommandActivator;

/**
 *
 * @author jdenise@redhat.com
 */
public class PatchRollbackActivator extends AbstractCommandActivator {

    @Override
    public boolean isActivated(ProcessedCommand command) {
        try {
            AbstractDistributionCommand cmd = (AbstractDistributionCommand) command.getCommand();
            return !getPatches(getCommandContext(), cmd, cmd.getPatchStream(), cmd.getHost()).isEmpty();
        } catch (Exception ex) {
            // Could be, among other problems an invalid installation (such as in test)
            return true;
        }
    }

    private static List<ModelNode> getAllPatches(PatchOperationTarget target, String stream) throws PatchingException {
        if (stream == null) {
            PatchOperationBuilder streams = PatchOperationBuilder.Factory.streams();
            // retrieve all streams.
            ModelNode response = streams.execute(target);
            final ModelNode result = response.get(ModelDescriptionConstants.RESULT);
            if (!result.isDefined()) {
                return Collections.emptyList();
            }
            // retrieve patches in stream.
            List<ModelNode> patches = new ArrayList<>();
            List<ModelNode> list = response.get(ModelDescriptionConstants.RESULT).asList();
            for (ModelNode s : list) {
                patches.addAll(getPatches(target, s.asString()));
            }
            return patches;
        } else {
            return getPatches(target, stream);
        }
    }

    private static List<ModelNode> getPatches(PatchOperationTarget target, String stream) throws PatchingException {
        PatchOperationBuilder history = PatchOperationBuilder.Factory.history(stream);
        ModelNode resp = history.execute(target);
        ModelNode res = resp.get(ModelDescriptionConstants.RESULT);
        return res.asList();
    }

    static List<ModelNode> getPatches(CommandContext ctx, AbstractDistributionCommand cmd, String stream, String host) throws PatchingException, CommandException {
        if (ctx.isDomainMode()) {
            // Lookup hosts.
            List<String> hosts = host == null ? Util.getHosts(ctx.getModelControllerClient()) : Arrays.asList(host);
            List<ModelNode> patches = new ArrayList<>();
            for (String h : hosts) {
                patches.addAll(getAllPatches(PatchOperationTarget.
                        createHost(h, ctx.getModelControllerClient()), null));
            }
            return patches;
        } else {
            return getPatches(cmd.createPatchOperationTarget(ctx), stream);
        }
    }
}
