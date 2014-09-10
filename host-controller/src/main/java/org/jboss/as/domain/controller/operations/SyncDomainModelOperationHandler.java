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

import java.util.Collection;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.mgmt.HostInfo;
import org.jboss.dmr.ModelNode;

/**
 * Operation handler synchronizing the domain model.
 *
 * @author Emanuel Muckenhuber
 */
public class SyncDomainModelOperationHandler implements OperationStepHandler {

    private final boolean ignoreUnused;
    private final ExtensionRegistry extensionRegistry;
    private final IgnoredDomainResourceRegistry ignoredResourceRegistry;
    private final Collection<IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo> serverConfigs;

    public SyncDomainModelOperationHandler(HostInfo hostInfo, ExtensionRegistry extensionRegistry, IgnoredDomainResourceRegistry ignoredResourceRegistry) {
        this.ignoreUnused = hostInfo.isIgnoreUnaffectedConfig();
        this.extensionRegistry = extensionRegistry;
        this.ignoredResourceRegistry = ignoredResourceRegistry;
        this.serverConfigs = hostInfo.getServerConfigInfos();
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();

        final ModelNode readOp = new ModelNode();
        readOp.get(OP).set(ReadMasterDomainModelHandler.OPERATION_NAME);
        readOp.get(OP_ADDR).setEmptyList();

        final ReadMasterDomainOperationsHandler h = new ReadMasterDomainOperationsHandler(ignoreUnused, serverConfigs, extensionRegistry);
        final ModelNode localOperations = new ModelNode();

        context.addStep(localOperations, readOp, h, OperationContext.Stage.MODEL);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ModelNode result = localOperations.get(RESULT);
                final SyncModelOperationHandler handler = new SyncModelOperationHandler(result.asList(), ignoredResourceRegistry);
                context.addStep(operation, handler, OperationContext.Stage.MODEL);
                context.stepCompleted();
            }
        }, OperationContext.Stage.MODEL);

        context.stepCompleted();
    }

}
