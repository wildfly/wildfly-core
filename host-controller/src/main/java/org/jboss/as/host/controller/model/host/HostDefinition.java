/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.host.controller.model.host;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISCOVERY_OPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_ENVIRONMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMESPACES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELEASE_CODENAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELEASE_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SCHEMA_LOCATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostModelUtil;
import org.jboss.as.host.controller.discovery.DiscoveryOptionsResource;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.operations.HostAddHandler;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.as.platform.mbean.PlatformMBeanConstants;
import org.jboss.as.platform.mbean.RootPlatformMBeanResource;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.ModuleClassLoader;

public class HostDefinition extends SimpleResourceDefinition {

    private final ManagementResourceRegistration root;
    private final HostControllerEnvironment environment;
    private final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;
    private final HostModelUtil.HostModelRegistrar hostModelRegistrar;
    private final ProcessType processType;
    private final DelegatingConfigurableAuthorizer authorizer;
    private final Resource modelControllerResource;
    private final LocalHostControllerInfo localHostControllerInfo;

    public HostDefinition(
            final ManagementResourceRegistration root,
            final HostControllerEnvironment environment,
            final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
            final HostModelUtil.HostModelRegistrar hostModelRegistrar,
            final ProcessType processType,
            final DelegatingConfigurableAuthorizer authorizer,
            final Resource modelControllerResource,
            final LocalHostControllerInfoImpl localHostControllerInfo) {
        super(new Parameters(PathElement.pathElement(HOST), HostModelUtil.getResourceDescriptionResolver()));
        this.root = root;
        this.environment = environment;
        this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
        this.hostModelRegistrar = hostModelRegistrar;
        this.processType = processType;
        this.authorizer = authorizer;
        this.modelControllerResource = modelControllerResource;
        this.localHostControllerInfo = localHostControllerInfo;
    }

    public LocalHostControllerInfo getLocalHostControllerInfo() {
        return localHostControllerInfo;
    }

    public void registerHostModel(final String hostName) {
        hostModelRegistrar.registerHostModel(hostName, root);
    }

    public void initCoreModel(final ModelNode model) {
        initCoreModel(model, environment);
    }

    public void initModelServices(final OperationContext context, final PathAddress hostAddress, final Resource rootResource) {
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

    @Override
    public void registerOperations(ManagementResourceRegistration hostDefinition) {
        super.registerOperations(hostDefinition);
        hostDefinition.registerOperationHandler(HostAddHandler.DEFINITION, new HostAddHandler(this));
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
