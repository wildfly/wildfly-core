/*
 *
 *  * JBoss, Home of Professional Open Source.
 *  * Copyright 2013, Red Hat, Inc., and individual contributors
 *  * as indicated by the @author tags. See the copyright.txt file in the
 *  * distribution for a full listing of individual contributors.
 *  *
 *  * This is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU Lesser General Public License as
 *  * published by the Free Software Foundation; either version 2.1 of
 *  * the License, or (at your option) any later version.
 *  *
 *  * This software is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  * Lesser General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Lesser General Public
 *  * License along with this software; if not, write to the Free
 *  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 */
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo;
import org.jboss.dmr.ModelNode;

/**
 * Executed on the DC when a slave's host requests more data for a given server group.
 *
 * This is the remote counterpart of the {@code SyncServerConfigOperationHandler}.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class PullDownDataForServerConfigOnSlaveHandler implements OperationStepHandler {

    public static String OPERATION_NAME = "slave-server-config-change";

    private final String host;
    private final Transformers transformers;
    private final ExtensionRegistry extensionRegistry;

    public PullDownDataForServerConfigOnSlaveHandler(final String hostName, final Transformers transformers, final ExtensionRegistry extensionRegistry) {
        this.host = hostName;
        this.transformers = transformers;
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ServerConfigInfo serverConfig = IgnoredNonAffectedServerGroupsUtil.createServerConfigInfo(operation.require(SERVER));

        // Filter the information to only include configuration for the given server-group or socket-binding group
        final ReadOperationsHandlerUtils.RequiredConfigurationHolder rc = new ReadOperationsHandlerUtils.RequiredConfigurationHolder();
        ReadOperationsHandlerUtils.processServerConfig(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS), rc, serverConfig, extensionRegistry);
        final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry = ReadOperationsHandlerUtils.createServerIgnoredRegistry(rc);

        final ReadDomainModelHandler handler = new ReadDomainModelHandler(ignoredTransformationRegistry, transformers);
        context.addStep(handler, OperationContext.Stage.MODEL);
        context.stepCompleted();
    }

}
