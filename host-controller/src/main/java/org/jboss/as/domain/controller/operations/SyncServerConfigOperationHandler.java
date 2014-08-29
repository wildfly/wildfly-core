/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.dmr.ModelNode;

/**
 * Operation handler synchronizing the server model.
 *
 * @author Emanuel Muckenhuber
 */
public class SyncServerConfigOperationHandler implements OperationStepHandler {

    private final IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo serverConfig;
    private final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;
    private final ExtensionRegistry extensionRegistry;

    public SyncServerConfigOperationHandler(IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo serverConfig, IgnoredDomainResourceRegistry ignoredDomainResourceRegistry, ExtensionRegistry extensionRegistry) {
        this.serverConfig = serverConfig;
        this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public void execute(OperationContext context, final ModelNode operation) throws OperationFailedException {

        final ModelNode descibeOp = new ModelNode();
        descibeOp.get(OP).set("sync");
        descibeOp.get(OP_ADDR).setEmptyList();
        descibeOp.get(SERVER).set(serverConfig.toModelNode());

        final PullDownDataForServerConfigOnSlaveHandler handler = new PullDownDataForServerConfigOnSlaveHandler(extensionRegistry);

        final ModelNode localOperations = new ModelNode();
        context.addStep(localOperations, descibeOp, handler, OperationContext.Stage.MODEL);

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ModelNode result = localOperations.get(RESULT);
                final SyncModelOperationHandler handler = new SyncModelOperationHandler(result.asList(), ignoredDomainResourceRegistry);
                context.addStep(operation, handler, OperationContext.Stage.MODEL);
                context.stepCompleted();
            }
        }, OperationContext.Stage.MODEL);
        context.stepCompleted();
    }

}
