/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.NoopOperationStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.Action.ActionEffect;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.ResourceAuthorization;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractOperationTestCase {
    MockOperationContext getOperationContext() {
        return getOperationContext(false);
    }

    MockOperationContext getOperationContext(final boolean booting) {
        final Resource root = createRootResource();
        return new MockOperationContext(root, booting, PathAddress.EMPTY_ADDRESS);
    }

    MockOperationContext getOperationContext(final PathAddress operationAddress) {
        final Resource root = createRootResource();
        return new MockOperationContext(root, false, operationAddress);
    }

    static class OperationAndHandler {
        public final ModelNode operation;
        public final OperationStepHandler handler;

        OperationAndHandler(ModelNode operation, OperationStepHandler handler) {
            this.operation = operation;
            this.handler = handler;
        }
    }

    class MockOperationContext implements OperationContext {
        Resource root;
        private final boolean booting;
        final PathAddress operationAddress;
        final ManagementResourceRegistration registration;
        private Set<PathAddress> expectedSteps = new HashSet<PathAddress>();
        private final Map<AttachmentKey<?>, Object> valueAttachments = new HashMap<AttachmentKey<?>, Object>();
        private final ModelNode result = new ModelNode();
        private final boolean failOnUnexpected;
        private final Map<Stage, List<OperationAndHandler>> addedSteps = new HashMap<Stage, List<OperationAndHandler>>();

        protected MockOperationContext(final Resource root, final boolean booting, final PathAddress operationAddress,
                                       boolean failOnUnexpected) {
            this.root = root;
            this.booting = booting;
            this.operationAddress = operationAddress;
            this.failOnUnexpected = failOnUnexpected;
            this.registration = createManagementResourceRegistration(operationAddress);
        }

        protected MockOperationContext(final Resource root, final boolean booting, final PathAddress operationAddress, AttributeDefinition... attributes) {
            this(root, booting, operationAddress, true);
            OperationEntry entry = mock(OperationEntry.class);
            doReturn(SimpleOperationDefinitionBuilder.of(ModelDescriptionConstants.ADD, mock(ResourceDescriptionResolver.class)).setParameters(attributes).build()).when(entry).getOperationDefinition();
            doReturn(entry).when(this.registration).getOperationEntry(PathAddress.EMPTY_ADDRESS, ModelDescriptionConstants.ADD);
            for (AttributeDefinition attribute : attributes) {
                AttributeAccess access = mock(AttributeAccess.class);
                doReturn(AttributeAccess.AccessType.READ_WRITE).when(access).getAccessType();
                doReturn(AttributeAccess.Storage.CONFIGURATION).when(access).getStorageType();
                doReturn(ModelOnlyWriteAttributeHandler.INSTANCE).when(access).getWriteHandler();
                doReturn(attribute).when(access).getAttributeDefinition();
                doReturn(access).when(this.registration).getAttributeAccess(PathAddress.EMPTY_ADDRESS, attribute.getName());
            }
            doReturn(Stream.of(attributes).map(AttributeDefinition::getName).collect(Collectors.toSet())).when(this.registration).getAttributeNames(PathAddress.EMPTY_ADDRESS);
        }

        public void expectStep(final PathAddress address) {
            this.expectedSteps.add(address);
        }

        public Map<Stage, List<OperationAndHandler>> verify() {
            if (!expectedSteps.isEmpty()) {
                System.out.println("Missing: " + expectedSteps);
                fail("Not all the expected steps were added. " + expectedSteps);
            }
            return addedSteps;
        }

        @Override
        public void addStep(OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            if (stage == Stage.RUNTIME) {
                fail("Should not have added step");
            }
        }

        @Override
        public void addStep(OperationStepHandler step, Stage stage, boolean addFirst) throws IllegalArgumentException {
            addStep(new ModelNode().setEmptyObject(), step, stage, addFirst);
        }

        @Override
        public void addStep(ModelNode operation, OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            addStep(operation, step, stage, false);
        }
        @Override
        public void addStep(ModelNode operation, OperationStepHandler step, OperationContext.Stage stage, boolean addFirst) throws IllegalArgumentException {
            final PathAddress opAddress = PathAddress.pathAddress(operation.get(OP_ADDR));
            if (!expectedSteps.contains(opAddress) && failOnUnexpected) {
                if (opAddress.size() == 2){
                    //Ignore the add/removing running server add step done by ServerAddHandler and ServerRemoveHandler
                    if (opAddress.getElement(0).getKey().equals(HOST) && opAddress.getElement(1).getKey().equals(SERVER) &&
                            (operation.get(OP).asString().equals(ADD) || operation.get(OP).asString().equals(REMOVE))){
                        return;
                    }

                }
                if (stage != Stage.VERIFY) {
                    fail("Should not have added step for: " + opAddress);
                }
            }
            expectedSteps.remove(opAddress);
            List<OperationAndHandler> stageList = addedSteps.get(stage);
            if (stageList == null) {
                stageList = new ArrayList<OperationAndHandler>();
                addedSteps.put(stage, stageList);
            }
            OperationAndHandler oah = new OperationAndHandler(operation, step);
            if (addFirst) {
                stageList.add(0, oah);
            } else {
                stageList.add(oah);
            }
        }

        @Override
        public void addStep(ModelNode response, ModelNode operation, OperationStepHandler step, Stage stage, boolean addFirst) throws IllegalArgumentException {
            addStep(operation, step, stage);
        }

        @Override
        public void addStep(ModelNode response, ModelNode operation, OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            fail("Should not have added step");
        }

        @Override
        public void addModelStep(OperationDefinition stepDefinition, OperationStepHandler stepHandler, boolean addFirst) throws IllegalArgumentException {
            addStep(stepHandler, Stage.MODEL);
        }

        @Override
        public void addModelStep(ModelNode response, ModelNode operation, OperationDefinition stepDefinition, OperationStepHandler stepHandler, boolean addFirst) throws IllegalArgumentException {
            addStep(operation, stepHandler, Stage.MODEL);
        }

        @Override
        public PathAddress getCurrentAddress() {
            return operationAddress;
        }

        @Override
        public final String getCurrentOperationName() {
            return null;
        }

        @Override
        public final ModelNode getCurrentOperationParameter(final String parameterName) {
            return null;
        }

        @Override
        public final ModelNode getCurrentOperationParameter(final String parameterName, boolean nullable) {
            return null;
        }

        @Override
        public String getCurrentAddressValue() {
            if (operationAddress.size() == 0) {
                throw new IllegalStateException();
            }
            return operationAddress.getLastElement().getValue();
        }

        @Override
        public InputStream getAttachmentStream(int index) {
            return null;
        }

        @Override
        public int getAttachmentStreamCount() {
            return 0;
        }

        @Override
        public ModelNode getResult() {
            return result;
        }

        @Override
        public boolean hasResult() {
            return false;
        }

        @Override
        public String attachResultStream(String mimeType, InputStream stream) {
            return "0";
        }

        @Override
        public void attachResultStream(String uuid, String mimeType, InputStream stream) {
            // no-op
        }

        @Override
        public ModelNode getServerResults() {
            return null;
        }

        @Override
        public void completeStep(OperationContext.RollbackHandler rollbackHandler) {
        }

        @Override
        public void completeStep(ResultHandler resultHandler) {
        }



        @Override
        public ModelNode getFailureDescription() {
            return null;
        }

        @Override
        public boolean hasFailureDescription() {
            return false;
        }

        @Override
        public ModelNode getResponseHeaders() {
            return null;
        }

        @Override
        public ProcessType getProcessType() {
            return ProcessType.HOST_CONTROLLER;
        }

        @Override
        public RunningMode getRunningMode() {
            return null;
        }

        @Override
        public boolean isBooting() {
            return booting;
        }

        @Override
        public boolean isRollbackOnly() {
            return false;
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public boolean isRollbackOnRuntimeFailure() {
            return false;
        }

        @Override
        public boolean isResourceServiceRestartAllowed() {
            return false;
        }

        @Override
        public void reloadRequired() {
        }

        public boolean isReloadRequired() {
            return false;
        }

        @Override
        public void restartRequired() {
        }

        @Override
        public void revertReloadRequired() {
        }

        @Override
        public void revertRestartRequired() {
        }

        @Override
        public void runtimeUpdateSkipped() {
        }

        @Override
        public ImmutableManagementResourceRegistration getResourceRegistration() {
            return getResourceRegistrationForUpdate();
        }

        @Override
        public ManagementResourceRegistration getResourceRegistrationForUpdate() {
            return this.registration;
        }

        @Override
        public ImmutableManagementResourceRegistration getRootResourceRegistration() {
            return null;
        }

        @Override
        public ServiceRegistry getServiceRegistry(boolean modify) throws UnsupportedOperationException {
            return null;
        }

        @Override
        public ServiceController<?> removeService(ServiceName name) throws UnsupportedOperationException {
            return null;
        }

        @Override
        public void removeService(ServiceController<?> controller) throws UnsupportedOperationException {
        }

        public CapabilityServiceTarget getServiceTarget() throws UnsupportedOperationException {
            return null;
        }

        @Override
        public CapabilityServiceTarget getCapabilityServiceTarget() throws UnsupportedOperationException {
            return null;
        }

        @Override
        public ServiceName getCapabilityServiceName(String capabilityBaseName, Class<?> serviceType, String... dynamicParts) {
            return null;
        }

        public ModelNode readModel(PathAddress address) {
            return null;
        }

        public ModelNode readModelForUpdate(PathAddress address) {
            return null;
        }

        @Override
        public void acquireControllerLock() {
        }

        @Override
        public Resource createResource(PathAddress relativeAddress) {
            final Resource toAdd = Resource.Factory.create();
            addResource(relativeAddress, toAdd);
            return toAdd;
        }

        @Override
        public void addResource(PathAddress relativeAddress, Resource toAdd) {
            Resource model = root;
            final Iterator<PathElement> i = operationAddress.append(relativeAddress).iterator();
            while (i.hasNext()) {
                final PathElement element = i.next();
                if (element.isMultiTarget()) {
                    throw ControllerLogger.ROOT_LOGGER.cannotWriteTo("*");
                }
                if (!i.hasNext()) {
                    if (model.hasChild(element)) {
                        throw ControllerLogger.ROOT_LOGGER.duplicateResourceAddress(relativeAddress);
                    } else {
                        model.registerChild(element, toAdd);
                        model = toAdd;
                    }
                } else {
                    model = model.getChild(element);
                    if (model == null) {
                        PathAddress ancestor = PathAddress.EMPTY_ADDRESS;
                        for (PathElement pe : relativeAddress) {
                            ancestor = ancestor.append(pe);
                            if (element.equals(pe)) {
                                break;
                            }
                        }
                        throw ControllerLogger.ROOT_LOGGER.resourceNotFound(ancestor, relativeAddress);
                    }
                }
            }
        }

        @Override
        public void addResource(PathAddress address, int index, Resource toAdd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Resource readResource(PathAddress address) {
            return readResource(address, true);
        }

        @Override
        public Resource readResource(PathAddress relativeAddress, boolean recursive) {
            final PathAddress address = operationAddress.append(relativeAddress);
            return readResourceFromRoot(address, recursive);
        }

        @Override
        public Resource readResourceFromRoot(PathAddress address) {
            return readResourceFromRoot(address, true);
        }

        @Override
        public Resource readResourceFromRoot(PathAddress address, boolean recursive) {
            Resource resource = this.root;
            if (recursive) {
                for (PathElement element : address) {
                    resource = resource.requireChild(element);
                }
            }
            return resource;
        }

        @Override
        public Resource readResourceForUpdate(PathAddress address) {
            return readResource(address);
        }

        @Override
        public Resource removeResource(PathAddress address) throws UnsupportedOperationException {
            return null;
        }

        public Resource getRootResource() {
            return root;
        }

        @Override
        public Resource getOriginalRootResource() {
            return root;
        }

        @Override
        public boolean isModelAffected() {
            return false;
        }

        @Override
        public boolean isResourceRegistryAffected() {
            return false;
        }

        @Override
        public boolean isRuntimeAffected() {
            return false;
        }

        @Override
        public OperationContext.Stage getCurrentStage() {
            return null;
        }

        @Override
        public void report(MessageSeverity severity, String message) {
        }

        @Override
        public boolean markResourceRestarted(PathAddress resource, Object owner) {
            return false;
        }

        @Override
        public boolean revertResourceRestarted(PathAddress resource, Object owner) {
            return false;
        }

        @Override
        public ModelNode resolveExpressions(ModelNode node) throws OperationFailedException {
            return ExpressionResolver.TEST_RESOLVER.resolveExpressions(node);
        }

        @Override
        public <V> V getAttachment(final AttachmentKey<V> key) {
            return key.cast(valueAttachments.get(key));
        }

        @Override
        public <V> V attach(final AttachmentKey<V> key, final V value) {
            return key.cast(valueAttachments.put(key, value));
        }

        @Override
        public <V> V attachIfAbsent(final AttachmentKey<V> key, final V value) {
            return key.cast(valueAttachments.put(key, value));
        }

        @Override
        public <V> V detach(final AttachmentKey<V> key) {
            return key.cast(valueAttachments.get(key));
        }

        @Override
        public AuthorizationResult authorize(ModelNode operation) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public AuthorizationResult authorize(ModelNode operation, Set<Action.ActionEffect> effects) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public AuthorizationResult authorize(ModelNode operation, String attribute, ModelNode currentValue) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public AuthorizationResult authorize(ModelNode operation, String attribute, ModelNode currentValue, Set<Action.ActionEffect> effects) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public boolean isNormalServer() {
            return false;
        }

        @Override
        public AuthorizationResult authorizeOperation(ModelNode operation) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public ResourceAuthorization authorizeResource(boolean attributes, boolean isDefaultResource) {
            return new ResourceAuthorization() {

                @Override
                public AuthorizationResult getResourceResult(ActionEffect actionEffect) {
                    return AuthorizationResult.PERMITTED;
                }

                @Override
                public AuthorizationResult getOperationResult(String operationName) {
                    return AuthorizationResult.PERMITTED;
                }

                @Override
                public AuthorizationResult getAttributeResult(String attribute, ActionEffect actionEffect) {
                    return AuthorizationResult.PERMITTED;
                }
            };
        }

        @Override
        public SecurityIdentity getSecurityIdentity() {
            return null;
        }

        @Override
        public Environment getCallEnvironment() {
            throw null;
        }

        @Override
        public void registerCapability(RuntimeCapability capability) {
            // no-op
        }

        @Override
        public void registerAdditionalCapabilityRequirement(String required, String dependent, String attribute) {
            // no-op;
        }

        @Override
        public boolean hasOptionalCapability(String required, String dependent, String attribute) {
            return false;
        }

        @Override
        public void requireOptionalCapability(String required, String dependent, String attribute) throws OperationFailedException {
            // no-op;
        }

        @Override
        public void deregisterCapabilityRequirement(String required, String dependent) {
            deregisterCapabilityRequirement(required, dependent, null);
        }

        @Override
        public void deregisterCapabilityRequirement(String required, String dependent, String attribute) {
            // no-op;
        }

        @Override
        public void deregisterCapability(String capabilityName) {
            // no-op;
        }

        @Override
        public <T> T getCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) {
            return null;
        }

        @Override
        public <T> T getCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) {
            return null;
        }

        @Override
        public ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType) {
            return null;
        }

        @Override
        public ServiceName getCapabilityServiceName(String capabilitBaseyName, String dynamicPart, Class<?> serviceType) {
            return null;
        }

        @Override
        public CapabilityServiceSupport getCapabilityServiceSupport() {
            return null;
        }

        @Override
        public void emit(Notification notification) {
            // no-op
        }

        @Override
        public boolean isDefaultRequiresRuntime() {
            return false;
        }

        @Override
        public void addResponseWarning(Level level, String warning) {
            // TODO Auto-generated method stub
        }

        @Override
        public void addResponseWarning(Level level, ModelNode warning) {
            // TODO Auto-generated method stub
        }
    }

    static Resource createRootResource() {
        final Resource rootResource = Resource.Factory.create();

        CoreManagementResourceDefinition.registerDomainResource(rootResource, null);

        final Resource host = Resource.Factory.create();
        final Resource serverOneConfig = Resource.Factory.create();
        final ModelNode serverOneModel = new ModelNode();
        serverOneModel.get(GROUP).set("group-one");
        serverOneModel.get(SOCKET_BINDING_GROUP).set("binding-one");
        serverOneConfig.writeModel(serverOneModel);
        host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server-one"), serverOneConfig);

        final Resource serverTwoConfig = Resource.Factory.create();
        final ModelNode serverTwoModel = new ModelNode();
        serverTwoModel.get(GROUP).set("group-one");
        serverTwoConfig.writeModel(serverTwoModel);
        host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server-two"), serverTwoConfig);

        final Resource serverThreeConfig = Resource.Factory.create();
        final ModelNode serverThreeModel = new ModelNode();
        serverThreeModel.get(GROUP).set("group-two");
        serverThreeConfig.writeModel(serverThreeModel);
        host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server-three"), serverThreeConfig);

        rootResource.registerChild(PathElement.pathElement(HOST, "localhost"), host);

        final Resource serverGroup1 = Resource.Factory.create();
        serverGroup1.getModel().get(PROFILE).set("profile-one");
        serverGroup1.getModel().get(SOCKET_BINDING_GROUP).set("binding-one");
        rootResource.registerChild(PathElement.pathElement(SERVER_GROUP, "group-one"), serverGroup1);

        final Resource serverGroup2 = Resource.Factory.create();
        serverGroup2.getModel().get(PROFILE).set("profile-two");
        serverGroup2.getModel().get(SOCKET_BINDING_GROUP).set("binding-two");
        rootResource.registerChild(PathElement.pathElement(SERVER_GROUP, "group-two"), serverGroup2);

        final Resource profile1 = Resource.Factory.create();
        profile1.getModel().setEmptyObject();
        rootResource.registerChild(PathElement.pathElement(PROFILE, "profile-one"), profile1);
        final Resource profile2 = Resource.Factory.create();
        profile2.getModel().setEmptyObject();
        rootResource.registerChild(PathElement.pathElement(PROFILE, "profile-two"), profile2);

        final Resource binding1 = Resource.Factory.create();
        binding1.getModel().setEmptyObject();
        rootResource.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-one"), binding1);
        final Resource binding2 = Resource.Factory.create();
        binding2.getModel().setEmptyObject();
        rootResource.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-two"), binding2);

        final Resource management = rootResource.getChild(PathElement.pathElement(CORE_SERVICE, MANAGEMENT));
        final Resource accessControl = management.getChild(AccessAuthorizationResourceDefinition.PATH_ELEMENT);

        final Resource superUser = Resource.Factory.create();
        accessControl.registerChild(PathElement.pathElement(ROLE_MAPPING, "SuperUser"), superUser);
        final Resource include = Resource.Factory.create();
        superUser.registerChild(PathElement.pathElement(INCLUDE, "user-$local"), include);
        include.getModel().get("name").set("local");

        hack(rootResource, EXTENSION);
        hack(rootResource, PATH);
        hack(rootResource, SYSTEM_PROPERTY);
        hack(rootResource, INTERFACE);
        hack(rootResource, DEPLOYMENT);
        return rootResource;
    }

    static void hack(final Resource rootResource, final String type) {
        rootResource.registerChild(PathElement.pathElement(type, "hack"), Resource.Factory.create());
        for (Resource.ResourceEntry entry : rootResource.getChildren(type)) {
            rootResource.removeChild(entry.getPathElement());
        }
    }

    static final ManagementResourceRegistration createManagementResourceRegistration(PathAddress address) {
        ManagementResourceRegistration registration = mock(ManagementResourceRegistration.class);
        ProxyController proxyController = mock(ProxyController.class);
        when(registration.getAccessConstraints()).thenReturn(Collections.emptyList());
        when(registration.getAttributeNames(any())).thenReturn(Collections.emptySet());
        when(registration.getAttributes(any())).thenReturn(Collections.emptyMap());
        when(registration.getCapabilities()).thenReturn(Collections.emptySet());
        when(registration.getChildAddresses(any())).thenReturn(Collections.emptySet());
        when(registration.getChildNames(any())).thenReturn(Collections.emptySet());
        when(registration.getOperationHandler(any(), anyString())).thenReturn(NoopOperationStepHandler.WITHOUT_RESULT);
        when(registration.getOrderedChildTypes()).thenReturn(Collections.emptySet());
        when(registration.getPathAddress()).thenReturn(address);
        when(registration.getProcessType()).thenReturn(ProcessType.HOST_CONTROLLER);
        when(registration.getProxyController(any())).thenReturn(proxyController);
        when(registration.getSubModel(any())).thenReturn(registration);
        return registration;
    }
}
