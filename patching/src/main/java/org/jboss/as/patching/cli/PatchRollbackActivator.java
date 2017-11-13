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
