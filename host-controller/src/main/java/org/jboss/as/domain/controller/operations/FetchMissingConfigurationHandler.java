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

import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo;
import org.jboss.as.host.controller.mgmt.HostInfo;
import org.jboss.dmr.ModelNode;

/**
 * Executed on the DC when a slave's host requests more data for a given server group.
 *
 * This is the remote counterpart of the {@code SyncServerConfigOperationHandler}.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
public class FetchMissingConfigurationHandler implements OperationStepHandler {

    public static String OPERATION_NAME = "slave-server-config-change";

    private final Transformers transformers;
    private final ExtensionRegistry extensionRegistry;

    public FetchMissingConfigurationHandler(final String hostName, final Transformers transformers, final ExtensionRegistry extensionRegistry) {
        this.transformers = transformers;
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();

        // Filter the information to only include configuration for the given server-group or socket-binding group
        final Set<ServerConfigInfo> serverConfigs = IgnoredNonAffectedServerGroupsUtil.createConfigsFromModel(operation);
        final ReadMasterDomainModelUtil.RequiredConfigurationHolder rc = new ReadMasterDomainModelUtil.RequiredConfigurationHolder();
        for (final ServerConfigInfo serverConfig : serverConfigs) {
            ReadMasterDomainModelUtil.processServerConfig(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS), rc, serverConfig, extensionRegistry);
        }
        final Transformers.ResourceIgnoredTransformationRegistry manualExcludes = HostInfo.createIgnoredRegistry(operation);
        final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry = ReadMasterDomainModelUtil.createServerIgnoredRegistry(rc, manualExcludes);

        final ReadDomainModelHandler handler = new ReadDomainModelHandler(ignoredTransformationRegistry, transformers, true);
        context.addStep(handler, OperationContext.Stage.MODEL);
    }

}
