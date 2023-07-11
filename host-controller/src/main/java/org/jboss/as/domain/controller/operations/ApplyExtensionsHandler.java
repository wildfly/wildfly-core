/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_MODEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.ROOT_LOGGER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.extension.ExtensionResource;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.domain.controller.operations.coordination.DomainServerUtils;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.server.operations.ServerProcessStateHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;

/**
 * Step handler responsible for adding the extensions as part of the host registration process.
 *
 * @author Emanuel Muckenhuber
 */
public class ApplyExtensionsHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "resolve-subsystems";
    private static final ModelNode APPLY_EXTENSIONS = new ModelNode();

    static {
        APPLY_EXTENSIONS.get(OP).set(ApplyExtensionsHandler.OPERATION_NAME);
        APPLY_EXTENSIONS.get(OPERATION_HEADERS, "execute-for-coordinator").set(true);
        APPLY_EXTENSIONS.get(OP_ADDR).setEmptyList();
        APPLY_EXTENSIONS.protect();
    }

    private final ExtensionRegistry extensionRegistry;
    private final LocalHostControllerInfo localHostInfo;
    private final IgnoredDomainResourceRegistry ignoredResourceRegistry;

    //Private method does not need resources for description
    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, null)
        .setPrivateEntry()
        .build();

    public static Operation getOperation(List<ModelNode> extensions) {
        final List<ModelNode> bootOperations = new ArrayList<ModelNode>();
        for (final ModelNode extension : extensions) {
            final ModelNode e = new ModelNode();
            e.get("domain-resource-address").add(EXTENSION, extension.asString());
            bootOperations.add(e);
        }
        final ModelNode operation = APPLY_EXTENSIONS.clone();
        operation.get(DOMAIN_MODEL).set(bootOperations);
        return OperationBuilder.create(operation).build();
    }

    public ApplyExtensionsHandler(ExtensionRegistry extensionRegistry, LocalHostControllerInfo localHostInfo, final IgnoredDomainResourceRegistry ignoredResourceRegistry) {
        this.extensionRegistry = extensionRegistry;
        this.localHostInfo = localHostInfo;
        this.ignoredResourceRegistry = ignoredResourceRegistry;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // We get the model as a list of resources descriptions
        final ModelNode domainModel = operation.get(DOMAIN_MODEL);
        final ModelNode startRoot = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS));

        final ManagementResourceRegistration rootRegistration = context.getResourceRegistrationForUpdate();
        final Resource rootResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        for(Resource.ResourceEntry entry : rootResource.getChildren(EXTENSION)) {
            rootResource.removeChild(entry.getPathElement());
        }
        final Map<String, Resource> installedExtensions = new HashMap<>();
        final Set<String> appliedExtensions = new HashSet<String>(extensionRegistry.getExtensionModuleNames());
        for (final ModelNode resourceDescription : domainModel.asList()) {
            final PathAddress resourceAddress = PathAddress.pathAddress(resourceDescription.require(ReadMasterDomainModelUtil.DOMAIN_RESOURCE_ADDRESS));
            if (ignoredResourceRegistry.isResourceExcluded(resourceAddress)) {
                continue;
            }

            final Resource resource = getResource(resourceAddress, rootResource, context);
            if (resourceAddress.size() == 1 && resourceAddress.getElement(0).getKey().equals(EXTENSION)) {
                final String module = resourceAddress.getElement(0).getValue();
                if (appliedExtensions.add(module)) {
                    initializeExtension(module, rootRegistration);
                    installedExtensions.put(module, resource);
                }
            } else {
                continue;
            }
            resource.writeModel(resourceDescription.get(ReadMasterDomainModelUtil.DOMAIN_RESOURCE_MODEL));
        }
        if (!context.isBooting()) {
            final Resource domainRootResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            final ModelNode endRoot = Resource.Tools.readModel(domainRootResource);
            final Set<ServerIdentity> affectedServers = new HashSet<ServerIdentity>();
            final ModelNode hostModel = endRoot.require(HOST).asPropertyList().iterator().next().getValue();
            final Map<String, ProxyController> serverProxies = DomainServerUtils.getServerProxies(localHostInfo.getLocalHostName(), domainRootResource, context.getResourceRegistration());

            final ModelNode startExtensions = startRoot.get(EXTENSION);
            final ModelNode finishExtensions = endRoot.get(EXTENSION);
            if (!startExtensions.equals(finishExtensions)) {
                // This affects all servers
                affectedServers.addAll(DomainServerUtils.getAllRunningServers(hostModel, localHostInfo.getLocalHostName(), serverProxies));
            }

            if (!affectedServers.isEmpty()) {
                ROOT_LOGGER.domainModelChangedOnReConnect(affectedServers);
                final Set<ServerIdentity> runningServers = DomainServerUtils.getAllRunningServers(hostModel, localHostInfo.getLocalHostName(), serverProxies);
                for (ServerIdentity serverIdentity : affectedServers) {
                    if(!runningServers.contains(serverIdentity)) {
                        continue;
                    }
                    final PathAddress serverAddress = PathAddress.pathAddress(PathElement.pathElement(HOST, serverIdentity.getHostName()), PathElement.pathElement(SERVER, serverIdentity.getServerName()));
                    final OperationStepHandler handler = context.getResourceRegistration().getOperationHandler(serverAddress, ServerProcessStateHandler.REQUIRE_RELOAD_OPERATION);
                    final ModelNode op = new ModelNode();
                    op.get(OP).set(ServerProcessStateHandler.REQUIRE_RELOAD_OPERATION);
                    op.get(OP_ADDR).set(serverAddress.toModelNode());
                    context.addStep(op, handler, OperationContext.Stage.MODEL, true);
                }
            }
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                for (Map.Entry<String, Resource> entry : installedExtensions.entrySet()) {
                    String module = entry.getKey();
                    extensionRegistry.removeExtension(entry.getValue(), module, rootRegistration);
                }
            }
        });
    }

    private Resource getResource(PathAddress resourceAddress, Resource rootResource, OperationContext context) {
        if(resourceAddress.size() == 0) {
            return rootResource;
        }
        Resource temp = rootResource;
        int idx = 0;
        for(PathElement element : resourceAddress) {
            temp = temp.getChild(element);
            if(temp == null) {
                if (idx == 0) {
                    String type = element.getKey();
                    if (type.equals(EXTENSION)) {
                        // Needs a specialized resource type
                        temp = new ExtensionResource(element.getValue(), extensionRegistry);
                        context.addResource(resourceAddress, temp);
                    }
                }
                if (temp == null) {
                    temp = context.createResource(resourceAddress);
                }
                break;
            }
            idx++;
        }
        return temp;
    }

    protected void initializeExtension(String module, ManagementResourceRegistration rootRegistration) throws OperationFailedException {
        try {
            for (final Extension extension : Module.loadServiceFromCallerModuleLoader(ModuleIdentifier.fromString(module), Extension.class)) {
                if (rootRegistration.enables(extension)) {
                    ClassLoader oldTccl = SecurityActions.setThreadContextClassLoader(extension.getClass());
                    try {
                        extension.initializeParsers(extensionRegistry.getExtensionParsingContext(module, null));
                        extension.initialize(extensionRegistry.getExtensionContext(module, rootRegistration, ExtensionRegistryType.SLAVE));
                    } finally {
                        SecurityActions.setThreadContextClassLoader(oldTccl);
                    }
                }
            }
        } catch (ModuleLoadException e) {
            throw DomainControllerLogger.ROOT_LOGGER.failedToLoadModule(e, module);
        }
    }
}
