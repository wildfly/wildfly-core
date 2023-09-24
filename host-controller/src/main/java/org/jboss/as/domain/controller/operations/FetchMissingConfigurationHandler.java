/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
    private final Set<String> excludedExtensions;

    public FetchMissingConfigurationHandler(final String hostName, final Set<String> excludedExtensions, final Transformers transformers, final ExtensionRegistry extensionRegistry) {
        this.transformers = transformers;
        this.extensionRegistry = extensionRegistry;
        this.excludedExtensions = excludedExtensions;
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
        final Transformers.ResourceIgnoredTransformationRegistry manualExcludes = HostInfo.createIgnoredRegistry(operation, excludedExtensions);
        final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry = ReadMasterDomainModelUtil.createServerIgnoredRegistry(rc, manualExcludes);

        final ReadDomainModelHandler handler = new ReadDomainModelHandler(ignoredTransformationRegistry, transformers, true);
        context.addStep(handler, OperationContext.Stage.MODEL);
    }

}
