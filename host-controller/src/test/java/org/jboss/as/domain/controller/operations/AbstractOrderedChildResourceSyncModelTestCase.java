/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_MODEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.resources.ProfileResourceDefinition;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;
import org.jboss.as.host.controller.discovery.DiscoveryOption;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.as.host.controller.util.AbstractControllerTestBase;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class AbstractOrderedChildResourceSyncModelTestCase extends AbstractControllerTestBase {

    private final ExtensionRegistry extensionRegistry = new ExtensionRegistry(ProcessType.HOST_CONTROLLER, new RunningModeControl(RunningMode.NORMAL), null, null, RuntimeHostControllerInfoAccessor.SERVER);
    private volatile IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;

    static final PathElement HOST_ELEMEMT = PathElement.pathElement(HOST, "slave");
    static final PathElement PROFILE_ELEMENT = PathElement.pathElement(PROFILE, "test");
    static final PathElement SUBSYSTEM_ELEMENT = PathElement.pathElement(SUBSYSTEM, "test");
    static final PathElement ORDERED_CHILD = PathElement.pathElement("ordered-child");
    static final PathElement NON_ORDERED_CHILD = PathElement.pathElement("non-ordered-child");
    static final PathElement EXTRA_CHILD = PathElement.pathElement("extra-child");
    static final AttributeDefinition ATTR = new SimpleAttributeDefinitionBuilder("attr", ModelType.STRING, true).build();
    static final AttributeDefinition[] REQUEST_ATTRIBUTES = new AttributeDefinition[]{ATTR};
    static final OperationDefinition TRIGGER_SYNC = new SimpleOperationDefinitionBuilder("trigger-sync", new NonResolvingResourceDescriptionResolver())
        .addParameter(ATTR)
        .build();

    final boolean registerExtraChildren;
    final boolean localIndexedAdd;

    public AbstractOrderedChildResourceSyncModelTestCase(boolean registerExtraChildren, boolean localIndexedAdd) {
        this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
        this.registerExtraChildren = registerExtraChildren;
        this.localIndexedAdd = localIndexedAdd;
    }

    @Override
    protected void initModel(ManagementModel managementModel) {

        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);
        registration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);

        registration.registerOperationHandler(TRIGGER_SYNC, new TriggerSyncHandler());
        registration.registerOperationHandler(GenericModelDescribeOperationHandler.DEFINITION, GenericModelDescribeOperationHandler.INSTANCE, true);

        registration.registerSubModel(new ServerGroupResourceDefinition(false, HOST_INFO, new HostFileRepository() {

            @Override
            public File getDeploymentRoot(ContentReference reference) {
                return null;
            }

            @Override
            public File[] getDeploymentFiles(ContentReference reference) {
                return null;
            }

            @Override
            public void deleteDeployment(ContentReference reference) {
            }

            @Override
            public File getFile(String relativePath) {
                return null;
            }

            @Override
            public File getConfigurationFile(String relativePath) {
                return null;
            }
        }));

        ManagementResourceRegistration profileReg = registration.registerSubModel(new ProfileResourceDefinition(extensionRegistry));

        profileReg.registerSubModel(new SubsystemResourceDefinition());

        GlobalNotifications.registerGlobalNotifications(registration, processType);

        registerCommonChildren(managementModel.getRootResource(), localIndexedAdd);

        ignoredDomainResourceRegistry = new IgnoredDomainResourceRegistry(HOST_INFO);
    }

    void executeTriggerSyncOperation(Resource rootResource) throws Exception {
        ReadMasterDomainModelUtil util = ReadMasterDomainModelUtil.readMasterDomainResourcesForInitialConnect(null, new NoopTransformers(), rootResource, null);
        ModelNode op = Util.createEmptyOperation(TRIGGER_SYNC.getName(), PathAddress.EMPTY_ADDRESS);
        op.get(DOMAIN_MODEL).set(util.getDescribedResources());
        executeForResult(op);
    }

    Resource createMasterDcResources() {
        Resource rootResource = Resource.Factory.create();
        registerCommonChildren(rootResource, true);
        rootResource.removeChild(HOST_ELEMEMT);
        return rootResource;
    }

    void registerCommonChildren(Resource rootResource, boolean sortedChildren) {
        //This is needed by the handlers but not used. Without this we get an NPE
        Resource host = Resource.Factory.create();
        host.getModel().setEmptyObject();
        rootResource.registerChild(HOST_ELEMEMT, host);

        Resource serverConfig = Resource.Factory.create();
        serverConfig.getModel().get(GROUP).set("main");
        host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server"), serverConfig);

        Resource serverGroup = Resource.Factory.create();
        serverGroup.getModel().get(PROFILE).set("test");
        rootResource.registerChild(PathElement.pathElement(SERVER_GROUP, "main"), serverGroup);

        //Now register our tree of resources
        Resource profile = Resource.Factory.create();
        profile.getModel().setEmptyObject();
        rootResource.registerChild(PROFILE_ELEMENT, profile);

        Resource subsystem = sortedChildren ? Resource.Factory.create(false, Collections.singleton(ORDERED_CHILD.getKey())) : Resource.Factory.create();
        subsystem.getModel().setEmptyObject();
        profile.registerChild(SUBSYSTEM_ELEMENT, subsystem);

        createAndRegisterSubsystemChildren(subsystem, "apple");
        createAndRegisterSubsystemChildren(subsystem, "orange");


    }

    void createAndRegisterSubsystemChildren(Resource subsystem, String value) {
        createAndRegisterSubsystemChild(subsystem, ORDERED_CHILD.getKey(), value);
        createAndRegisterSubsystemChild(subsystem, NON_ORDERED_CHILD.getKey(), value);
    }

    Resource createAndRegisterSubsystemChild(Resource subsystem, String childType, String value) {
        return createAndRegisterSubsystemChild(subsystem, childType, value, -1);
    }

    Resource createAndRegisterSubsystemChild(Resource subsystem, String childType, String value, int index) {
        Resource resource = createChildResource(subsystem, childType, value, index);
        if (registerExtraChildren && childType.equals(ORDERED_CHILD.getKey())) {
            Resource childA = Resource.Factory.create();
            String valueA = "jam";
            childA.getModel().get(ATTR.getName()).set(valueA.toUpperCase(Locale.ENGLISH));
            resource.registerChild(PathElement.pathElement(EXTRA_CHILD.getKey(), valueA), childA);

            Resource childB = Resource.Factory.create();
            String valueB = "juice";
            childB.getModel().get(ATTR.getName()).set(valueB.toUpperCase(Locale.ENGLISH));
            resource.registerChild(PathElement.pathElement(EXTRA_CHILD.getKey(), valueB), childB);
        }
        return resource;
    }

    Resource createChildResource(Resource parent, String childType, String value, int index) {
        Resource resource = registerExtraChildren ? Resource.Factory.create(false, Collections.singleton(EXTRA_CHILD.getKey())) : Resource.Factory.create();
        resource.getModel().get(ATTR.getName()).set(value.toUpperCase(Locale.ENGLISH));
        if (index < 0) {
            parent.registerChild(PathElement.pathElement(childType, value.toLowerCase(Locale.ENGLISH)), resource);
        } else {
            parent.registerChild(PathElement.pathElement(childType, value.toLowerCase(Locale.ENGLISH)), index, resource);
        }
        return resource;
    }

    Resource createAndRegisterSubsystemChildFromRoot(Resource rootResource, String childType, String value) {
        return createAndRegisterSubsystemChild(findSubsystemResource(rootResource), childType, value);
    }

    Resource createAndRegisterSubsystemChildFromRoot(Resource rootResource, String childType, String value, int index) {
        return createAndRegisterSubsystemChild(findSubsystemResource(rootResource), childType, value, index);
    }

    Resource findSubsystemResource(Resource rootResource) {
        Resource subsystem = rootResource.requireChild(PROFILE_ELEMENT);
        return subsystem.requireChild(SUBSYSTEM_ELEMENT);
    }

    ModelNode readResourceRecursive() throws Exception {
        ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(RECURSIVE).set(true);
        return executeForResult(op);
    }

    ModelNode findSubsystemResource(ModelNode modelNode) {
        ModelNode current = modelNode;
        current = current.require(PROFILE_ELEMENT.getKey());
        current = current.require(PROFILE_ELEMENT.getValue());
        current = current.require(SUBSYSTEM_ELEMENT.getKey());
        current = current.require(SUBSYSTEM_ELEMENT.getValue());
        return current;
    }

    void compareSubsystemModels(ModelNode expected, ModelNode actual) {
        Assert.assertEquals(findSubsystemResource(expected), findSubsystemResource(actual));
    }

    void compare(Set<String> keys, String...expected) {
        String[] actual = keys.toArray(new String[keys.size()]);
        Assert.assertArrayEquals(expected, actual);
    }

    class SubsystemResourceDefinition extends SimpleResourceDefinition {

        public SubsystemResourceDefinition() {
            super(SUBSYSTEM_ELEMENT,
                    new NonResolvingResourceDescriptionResolver(),
                    new AbstractAddStepHandler(REQUEST_ATTRIBUTES) {
                        @Override
                        protected ResourceCreator getResourceCreator() {
                            return new OrderedResourceCreator(false, ORDERED_CHILD.getKey());
                        }

                    },
                    new ModelOnlyRemoveStepHandler());
        }


        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerSubModel(new OrderedChildResourceDefinition());
            resourceRegistration.registerSubModel(new NonOrderedChildResourceDefinition());
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        }
    }

    static class NonOrderedChildResourceDefinition extends SimpleResourceDefinition {

        public NonOrderedChildResourceDefinition() {
            super(NON_ORDERED_CHILD, new NonResolvingResourceDescriptionResolver(), new ModelOnlyAddStepHandler(REQUEST_ATTRIBUTES), new ModelOnlyRemoveStepHandler());
        }


        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(ATTR, null, new ModelOnlyWriteAttributeHandler(ATTR));
        }
    }

    abstract class AbstractChildResourceDefinition extends SimpleResourceDefinition {
        public AbstractChildResourceDefinition(PathElement element, OperationStepHandler addHandler) {
            super(element, new NonResolvingResourceDescriptionResolver(), addHandler, new ModelOnlyRemoveStepHandler());
        }

        @Override
        protected boolean isOrderedChildResource() {
            //'true' here adds the 'add-index' parameter to the add operation
            return localIndexedAdd;
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(ATTR, null, new ModelOnlyWriteAttributeHandler(ATTR));
        }

    }

    class OrderedChildResourceDefinition extends AbstractChildResourceDefinition {

        OrderedChildResourceDefinition() {
            super(ORDERED_CHILD,
                    new AbstractAddStepHandler((REQUEST_ATTRIBUTES)) {
                        @Override
                        protected ResourceCreator getResourceCreator() {
                            return new OrderedResourceCreator(true, EXTRA_CHILD.getKey());
                        }

                    });
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            if (registerExtraChildren) {
                resourceRegistration.registerSubModel(new ExtraChildResourceDefinition());
            }
        }
    }

    class ExtraChildResourceDefinition extends AbstractChildResourceDefinition {
        public ExtraChildResourceDefinition() {
            super(EXTRA_CHILD,
                    new AbstractAddStepHandler(REQUEST_ATTRIBUTES) {
                        @Override
                        protected ResourceCreator getResourceCreator() {
                            return new OrderedResourceCreator(true);
                        }

                    });
        }
    }

    class TriggerSyncHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            final ModelNode syncOperation = new ModelNode();
            syncOperation.get(OP).set("calculate-diff-and-sync");
            syncOperation.get(OP_ADDR).setEmptyList();
            syncOperation.get(DOMAIN_MODEL).set(operation.get(DOMAIN_MODEL));

            Resource original = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);

            final HostControllerRegistrationHandler.OperationExecutor internalExecutor = getControllerService().getInternalExecutor();

            final SyncServerGroupOperationHandler handler = new SyncServerGroupOperationHandler("slave", original, ignoredDomainResourceRegistry, extensionRegistry, internalExecutor);
            context.addStep(syncOperation, handler, OperationContext.Stage.MODEL, true);
        }
    }

    static final LocalHostControllerInfo HOST_INFO = new LocalHostControllerInfo() {
        public String getLocalHostName() {
            return "localhost";
        }

        public boolean isMasterDomainController() {
            return false;
        }

        public String getNativeManagementInterface() {
            return null;
        }

        public int getNativeManagementPort() {
            return 0;
        }

        public String getNativeManagementSecurityRealm() {
            return null;
        }

        public String getHttpManagementInterface() {
            return null;
        }

        public int getHttpManagementPort() {
            return 0;
        }

        public String getHttpManagementSecureInterface() {
            return null;
        }

        public int getHttpManagementSecurePort() {
            return 0;
        }

        public String getHttpManagementSecurityRealm() {
            return null;
        }

        public String getRemoteDomainControllerUsername() {
            return null;
        }

        public List<DiscoveryOption> getRemoteDomainControllerDiscoveryOptions() {
            return null;
        }

        public ControlledProcessState.State getProcessState() {
            return null;
        }

        @Override
        public boolean isRemoteDomainControllerIgnoreUnaffectedConfiguration() {
            return false;
        }

        @Override
        public Collection<String> getAllowedOrigins() {
            return Collections.EMPTY_LIST;
        }
    };


    static class NoopTransformers implements Transformers {

        @Override
        public TransformationTarget getTarget() {
            return null;
        }

        @Override
        public TransformedOperation transformOperation(TransformationContext context, ModelNode operation)
                throws OperationFailedException {
            return new TransformedOperation(operation, TransformedOperation.ORIGINAL_RESULT);
        }

        @Override
        public TransformedOperation transformOperation(OperationContext operationContext, ModelNode operation)
                throws OperationFailedException {
            return new TransformedOperation(operation, TransformedOperation.ORIGINAL_RESULT);
        }

        @Override
        public Resource transformResource(ResourceTransformationContext context, Resource resource)
                throws OperationFailedException {
            return resource;
        }

        @Override
        public Resource transformRootResource(OperationContext operationContext, Resource resource)
                throws OperationFailedException {
            return resource;
        }

        @Override
        public Resource transformRootResource(OperationContext operationContext, Resource resource, ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) throws OperationFailedException {
            return resource;
        }
    }
}