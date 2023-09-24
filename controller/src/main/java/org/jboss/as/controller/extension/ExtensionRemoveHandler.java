/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.extension;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * Base handler for the extension resource remove operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ExtensionRemoveHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = REMOVE;
    private final ExtensionRegistry extensionRegistry;
    private final ExtensionRegistryType extensionRegistryType;

    /**
     * For a server or domain.xml extension, this will be the root resource registration.<br/>
     * For a host.xml extension, this will be the host resource registration
     */
    private final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider;

    /**
     * Create the ExtensionRemoveHandler
     *
     * @param extensionRegistry the registry for extensions
     */
    public ExtensionRemoveHandler(final ExtensionRegistry extensionRegistry,
                                  final ExtensionRegistryType extensionRegistryType,
                                  final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider) {
        this.extensionRegistry = extensionRegistry;
        this.extensionRegistryType = extensionRegistryType;
        this.rootResourceRegistrationProvider = rootResourceRegistrationProvider;
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String module = context.getCurrentAddressValue();

        if(context.removeResource(PathAddress.EMPTY_ADDRESS) == null)  {
            throw  ControllerLogger.ROOT_LOGGER.managementResourceNotFound(context.getCurrentAddress());
        }

        final ManagementResourceRegistration rootRegistration = rootResourceRegistrationProvider.getRootResourceRegistrationForUpdate(context);
        try {
            extensionRegistry.removeExtension(context.readResourceFromRoot(rootRegistration.getPathAddress()), module, rootRegistration);
        } catch(IllegalStateException isex) {
            throw new OperationFailedException(isex.getMessage());
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                // Restore the extension to the ExtensionRegistry and the ManagementResourceRegistration tree
                ExtensionAddHandler.initializeExtension(extensionRegistry, module, rootRegistration, extensionRegistryType);
            }
        });
    }
}
