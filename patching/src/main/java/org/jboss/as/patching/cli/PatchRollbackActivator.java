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

import java.util.List;
import org.aesh.command.impl.internal.ProcessedCommand;
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
            if (getCommandContext().isDomainMode()) {
                // Lookup hosts.
                List<String> hosts = Util.getHosts(getCommandContext().getModelControllerClient());
                for (String host : hosts) {
                    boolean hasPatch = hasPatchs(PatchOperationTarget.
                            createHost(host, getCommandContext().getModelControllerClient()));
                    if (hasPatch) {
                        return true;
                    }
                }
                return false;
            } else {
                return hasPatchs(cmd.createPatchOperationTarget(getCommandContext()));
            }
        } catch (Exception ex) {
            return true;
        }
    }

    private boolean hasPatchs(PatchOperationTarget target) throws PatchingException {
        PatchOperationBuilder streams = PatchOperationBuilder.Factory.streams();
        // retrieve all streams.
        ModelNode response = streams.execute(target);
        final ModelNode result = response.get(ModelDescriptionConstants.RESULT);
        if (!result.isDefined()) {
            return true;
        }
        // retrieve patches in stream.
        List<ModelNode> list = response.get(ModelDescriptionConstants.RESULT).asList();
        for (ModelNode s : list) {
            PatchOperationBuilder history = PatchOperationBuilder.Factory.history(s.asString());
            ModelNode resp = history.execute(target);
            ModelNode res = resp.get(ModelDescriptionConstants.RESULT);
            List<ModelNode> patches = res.asList();
            if (!patches.isEmpty()) {
                return true;
            }
        }
        return false;
    }

}
