/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CUSTOM_DISCOVERY;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.discovery.DiscoveryOption;
import org.jboss.as.host.controller.discovery.DiscoveryOptionResourceDefinition;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;

/**
 * Handler for the discovery option resource's add operation.
 *
 * @author Farah Juma
 */
public class DiscoveryOptionAddHandler extends AbstractDiscoveryOptionAddHandler {

    private final LocalHostControllerInfoImpl hostControllerInfo;

    /**
     * Create the DiscoveryOptionAddHandler.
     *
     * @param hostControllerInfo the host controller info
     */
    public DiscoveryOptionAddHandler(final LocalHostControllerInfoImpl hostControllerInfo) {
        this.hostControllerInfo = hostControllerInfo;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        if (context.isBooting()) {
            populateHostControllerInfo(hostControllerInfo, context, operation);
        } else {
            context.reloadRequired();
        }
    }

    @Override
    protected void populateModel(final OperationContext context, final ModelNode operation,
            final Resource resource) throws  OperationFailedException {
        updateOptionsAttribute(context, operation, CUSTOM_DISCOVERY);
    }

    protected void populateHostControllerInfo(LocalHostControllerInfoImpl hostControllerInfo, OperationContext context,
            ModelNode operation) throws OperationFailedException {
        ModelNode codeNode = DiscoveryOptionResourceDefinition.CODE.resolveModelAttribute(context, operation);
        String discoveryOptionClassName = codeNode.isDefined() ? codeNode.asString() : null;

        ModelNode moduleNode = DiscoveryOptionResourceDefinition.MODULE.resolveModelAttribute(context, operation);
        String moduleName = moduleNode.isDefined() ? moduleNode.asString() : null;

        final Map<String, ModelNode> discoveryOptionProperties = new HashMap<String, ModelNode>();
        if (operation.hasDefined(DiscoveryOptionResourceDefinition.PROPERTIES.getName())) {
            for (Map.Entry<String, String> discoveryOptionProperty : DiscoveryOptionResourceDefinition.PROPERTIES.unwrap(context, operation).entrySet()) {
                discoveryOptionProperties.put(discoveryOptionProperty.getKey(), new ModelNode(discoveryOptionProperty.getValue()));
            }
        }

        try {
            ModuleIdentifier moduleID = moduleName != null
                    ? ModuleIdentifier.fromString(moduleName)
                    : Module.forClass(getClass()).getIdentifier();
            final Class<? extends DiscoveryOption> discoveryOptionClass = Module.loadClassFromCallerModuleLoader(moduleID, discoveryOptionClassName)
                    .asSubclass(DiscoveryOption.class);
            final Constructor<? extends DiscoveryOption> constructor = discoveryOptionClass.getConstructor(Map.class);
            final DiscoveryOption discoveryOption = constructor.newInstance(discoveryOptionProperties);
            hostControllerInfo.addRemoteDomainControllerDiscoveryOption(discoveryOption);
        } catch (Exception e) {
            throw HostControllerLogger.ROOT_LOGGER.cannotInstantiateDiscoveryOptionClass(discoveryOptionClassName, e.getLocalizedMessage());
        }
    }
}
