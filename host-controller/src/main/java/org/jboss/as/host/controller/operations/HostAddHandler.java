/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISCOVERY_OPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_ENVIRONMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMESPACES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELEASE_CODENAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELEASE_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SCHEMA_LOCATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostModelUtil;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.host.controller.discovery.DiscoveryOptionsResource;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.platform.mbean.PlatformMBeanConstants;
import org.jboss.as.platform.mbean.RootPlatformMBeanResource;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.ModuleClassLoader;

/**
 * The handler to add the local host definition to the DomainModel.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:kwills@redhat.com">Ken Wills</a>
 */
public class HostAddHandler implements OperationStepHandler {

    public static final OperationContext.AttachmentKey<Boolean> HOST_ADD_AFTER_BOOT = OperationContext.AttachmentKey.create(Boolean.class);

    public static final String HOST_CONTROLLER_TEMPORARY_NAME = "HOST-CONTROLLER-TEMPORARY-NAME";

    private static final RuntimeCapability<Void> HOST_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of("org.wildfly.host.controller", false)
            .build();

    public static final String OPERATION_NAME = "add";

    private static final SimpleAttributeDefinition NAME = SimpleAttributeDefinitionBuilder.create("name", ModelType.STRING)
            .setRequired(true)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, HostResolver.getResolver("host"))
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .addParameter(NAME)
            .build();

    private final HostControllerEnvironment hostControllerEnvironment;
    private final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;
    private final HostModelUtil.HostModelRegistrar hostModelRegistrar;
    private final Resource modelControllerResource;

    public HostAddHandler(final HostControllerEnvironment hostControllerEnvironment,
                          final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                          final HostModelUtil.HostModelRegistrar hostModelRegistrar,
                          final Resource modelControllerResource) {
        this.hostControllerEnvironment = hostControllerEnvironment;
        this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
        this.hostModelRegistrar = hostModelRegistrar;
        this.modelControllerResource = modelControllerResource;
    }

    /**
     * {@inheritDoc}
     */
    public void execute(OperationContext context, ModelNode operation) {

        final PathAddress pa = context.getCurrentAddress();
        // if we're not already at the root, call this at the root as an immediate step
        if (!pa.equals(PathAddress.EMPTY_ADDRESS)) {
            final ModelNode cloned = operation.clone();
            cloned.get(OP_ADDR).set(PathAddress.EMPTY_ADDRESS.toModelNode());
            cloned.get(ModelDescriptionConstants.NAME).set(pa.getLastElement().getValue());
            context.addStep(cloned, this, OperationContext.Stage.MODEL, true);
            return;
        }

        // see if a host has already been added.
        Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, false);
        if (root.getChildrenNames(HOST).size() > 0) {
            // there is a host already registered
            final String exists = root.getChildrenNames(HOST).iterator().next();
            throw HostControllerLogger.ROOT_LOGGER.cannotAddHostAlreadyRegistered(exists);
        }

        final String hostName = operation.require(ModelDescriptionConstants.NAME).asString();
        context.registerCapability(HOST_RUNTIME_CAPABILITY);

        final ManagementResourceRegistration rootRegistration = context.getResourceRegistrationForUpdate();
        hostModelRegistrar.registerHostModel(hostName, rootRegistration);

        final PathAddress hostAddress = PathAddress.pathAddress(PathElement.pathElement(HOST, hostName));
        final Resource rootResource = context.createResource(hostAddress);
        final ModelNode model = rootResource.getModel();

        initCoreModel(model, hostControllerEnvironment);

        Resource res = context.readResourceFromRoot(hostAddress, false);
        if (!res.getModel().hasDefined(DOMAIN_CONTROLLER)) {
            model.get(DOMAIN_CONTROLLER).setEmptyObject();
            if (!context.isBooting() || operation.hasDefined(HOST_CONTROLLER_TEMPORARY_NAME)) {
                // this is an empty config boot and using /host=foo:add()
                final ModelNode update = new ModelNode();
                update.get(OP_ADDR).set(hostAddress.toModelNode());
                update.get(OP).set(LocalDomainControllerAddHandler.OPERATION_NAME);
                context.attach(HOST_ADD_AFTER_BOOT, true);
                // enable op routing for the empty boot case. this will get reset after the first host add and reload.
                context.addStep(update, rootRegistration.getOperationHandler(hostAddress, LocalDomainControllerAddHandler.OPERATION_NAME), OperationContext.Stage.MODEL, true);
            }
        }

        // Create the management resources
        Resource management = context.createResource(hostAddress.append(PathElement.pathElement(CORE_SERVICE, MANAGEMENT)));
        if (modelControllerResource != null) {
            management.registerChild(PathElement.pathElement(SERVICE, MANAGEMENT_OPERATIONS), modelControllerResource);
        }

        //Create the empty host-environment resource
        context.addResource(hostAddress.append(PathElement.pathElement(CORE_SERVICE, HOST_ENVIRONMENT)), PlaceholderResource.INSTANCE);

        //Create the empty module-loading resource
        rootResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.MODULE_LOADING), PlaceholderResource.INSTANCE);

        //Create the empty capability registry resource
        rootResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.CAPABILITY_REGISTRY), PlaceholderResource.INSTANCE);

        // Wire in the platform mbean resources. We're bypassing the context.createResource API here because
        // we want to use our own resource type. But it's ok as the createResource calls above have taken the lock
        rootResource.registerChild(PlatformMBeanConstants.ROOT_PATH, new RootPlatformMBeanResource());
        // Wire in the ignored-resources resource
        Resource.ResourceEntry ignoredRoot = ignoredDomainResourceRegistry.getRootResource();
        rootResource.registerChild(ignoredRoot.getPathElement(), ignoredRoot);

        // Create the empty discovery options resource
        context.addResource(hostAddress.append(PathElement.pathElement(CORE_SERVICE, DISCOVERY_OPTIONS)), new DiscoveryOptionsResource());
    }

    private static void initCoreModel(final ModelNode root, HostControllerEnvironment environment) {

        try {
            root.get(RELEASE_VERSION).set(Version.AS_VERSION);
            root.get(RELEASE_CODENAME).set(Version.AS_RELEASE_CODENAME);
        } catch (RuntimeException e) {
            if (HostAddHandler.class.getClassLoader() instanceof ModuleClassLoader) {
                //The standalone tests can't get this info
                throw e;
            }
        }
        root.get(MANAGEMENT_MAJOR_VERSION).set(Version.MANAGEMENT_MAJOR_VERSION);
        root.get(MANAGEMENT_MINOR_VERSION).set(Version.MANAGEMENT_MINOR_VERSION);
        root.get(MANAGEMENT_MICRO_VERSION).set(Version.MANAGEMENT_MICRO_VERSION);

        // Community uses UNDEF values
        ModelNode nameNode = root.get(PRODUCT_NAME);
        ModelNode versionNode = root.get(PRODUCT_VERSION);

        if (environment != null) {
            String productName = environment.getProductConfig().getProductName();
            String productVersion = environment.getProductConfig().getProductVersion();

            if (productName != null) {
                nameNode.set(productName);
            }
            if (productVersion != null) {
                versionNode.set(productVersion);
            }
        }

        //Set empty lists for namespaces and schema-locations to pass model validation
        root.get(NAMESPACES).setEmptyList();
        root.get(SCHEMA_LOCATIONS).setEmptyList();
    }

}
