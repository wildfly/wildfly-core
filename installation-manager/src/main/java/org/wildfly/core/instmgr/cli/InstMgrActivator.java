/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.QUERY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SELECT;

import java.io.IOException;
import java.util.List;

import org.aesh.command.impl.internal.ParsedCommand;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.core.cli.command.aesh.activator.AbstractCommandActivator;
import org.wildfly.core.instmgr.InstMgrConstants;

/**
 * A general activator to disable the Installation Manager commands when the resource [/host=*]/core-service=installer is not
 * available. The resource could not be available if the server installations does not supply a valid Installation Manager SIP
 * implementation or when running on an embedded server or host.
 *
 * See org.wildfly.core.instmgr.InstMgrInitialization which is the class in charge of install the installation manager resource.
 */
public final class InstMgrActivator extends AbstractCommandActivator {
    private static final Logger LOG = Logger.getLogger(InstMgrActivator.class);

    @Override
    public boolean isActivated(ParsedCommand command) {
        try {
            final CommandContext ctx = getCommandContext();
            final ModelControllerClient client = ctx.getModelControllerClient();
            if (client != null) {
                ModelNode op = new ModelNode();
                op.get(OP).set(QUERY);
                ModelNode select = new ModelNode().addEmptyList();
                select.add(CORE_SERVICE);
                op.get(SELECT).set(select);

                if (ctx.isDomainMode()) {
                    op.get(ADDRESS).set(PathAddress.pathAddress(PathElement.pathElement(HOST, "*")).toModelNode());

                    ModelNode response = client.execute(op);
                    List<ModelNode> hosts = response.get(RESULT).asListOrEmpty();
                    for (ModelNode hostResult : hosts) {
                        if (hostResult.get(RESULT, CORE_SERVICE).has(InstMgrConstants.TOOL_NAME)) {
                            return true;
                        }
                    }
                } else {
                    op.get(ADDRESS).set(PathAddress.EMPTY_ADDRESS.toModelNode());
                    ModelNode response = client.execute(op);
                    ModelNode result = response.get(RESULT);
                    if (result.get(CORE_SERVICE).has(InstMgrConstants.TOOL_NAME)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            LOG.debug("An error occurred inspecting the server resources. Installation Manager Commands cannot be activated", e);
            return false;
        }
        return false;
    }
}
