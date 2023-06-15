/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.ResourceAuthorization;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.server.SecurityIdentity;


/**
 * A read-only {@linkplain OperationContext}, allowing read-only access to the current write model from a different
 * operation, preventing any writes from this context. Operations can acquire a controller lock to prevent other
 * writes happen until this operation is done.
 *
 * @author Emanuel Muckenhuber
 */
class ReadOnlyContext extends AbstractOperationContext {

    private final int operationId;
    private final ModelControllerImpl controller;
    private final AbstractOperationContext primaryContext;
    private final ModelControllerImpl.ManagementModelImpl managementModel;
    private Step lockStep;

    private final ConcurrentMap<AttachmentKey<?>, Object> valueAttachments = new ConcurrentHashMap<AttachmentKey<?>, Object>();

    ReadOnlyContext(final ProcessType processType, final RunningMode runningMode, final ModelController.OperationTransactionControl transactionControl,
                    final ControlledProcessState processState, final boolean booting, final ModelControllerImpl.ManagementModelImpl managementModel,
                    final AbstractOperationContext primaryContext, final ModelControllerImpl controller, final int operationId, final Supplier<SecurityIdentity> securityIdentitySupplier) {
        super(processType, runningMode, transactionControl, processState,
                booting, controller.getAuditLogger(), controller.getNotificationSupport(),
                controller, true, null, null, securityIdentitySupplier);
        this.primaryContext = primaryContext;
        this.controller = controller;
        this.operationId = operationId;
        this.managementModel = managementModel;
    }

    @Override
    ResultAction executeOperation() {
        // WFCORE-2 allow this thread to be treated as a controlling thread
        AbstractOperationContext.controllingThread.set(primaryContext.initiatingThread);
        try {
            return super.executeOperation();
        } finally {
            AbstractOperationContext.controllingThread.remove();
        }
    }

    @Override
    ModelControllerImpl.ManagementModelImpl getManagementModel() {
        return managementModel;
    }

    @Override
    void awaitServiceContainerStability() throws InterruptedException {
        // nothing here
    }

    @Override
    ConfigurationPersister.PersistenceResource createPersistenceResource() throws ConfigurationPersistenceException {
        // We don't persist
        return null;
    }

    @Override
    void operationRollingBack() {
        // don't need to do anything
    }

    @Override
    void waitForRemovals() throws InterruptedException {
        // nothing here
    }

    @Override
    boolean isReadOnly() {
        return true;
    }

    @Override
    ManagementResourceRegistration getRootResourceRegistrationForUpdate() {
        throw readOnlyContext();
    }

    @Override
    boolean isBootOperation() {
        // Read only context is used in a domain-mode controlling process in various call patterns related
        // to synchronize another process' config with the controlling process. Whether its use
        // counts as a 'boot operation' for the controlling process depends on whether that process
        // is itself booting.
        return isBooting();
    }

    @Override
    public InputStream getAttachmentStream(int index) {
        throw readOnlyContext();
    }

    @Override
    public int getAttachmentStreamCount() {
        return 0;
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
    public ImmutableManagementResourceRegistration getResourceRegistration() {
        return managementModel.getRootResourceRegistration().getSubModel(activeStep.address);
    }

    @Override
    public ManagementResourceRegistration getResourceRegistrationForUpdate() {
        throw readOnlyContext();
    }

    @Override
    public ImmutableManagementResourceRegistration getRootResourceRegistration() {
        return managementModel.getRootResourceRegistration();
    }

    @Override
    public ServiceRegistry getServiceRegistry(boolean modify) throws UnsupportedOperationException {
        if (modify) {
            throw readOnlyContext();
        }
        return primaryContext.getServiceRegistry(false);
    }

    @Override
    public ServiceController<?> removeService(ServiceName name) throws UnsupportedOperationException {
        throw readOnlyContext();
    }

    @Override
    public void removeService(ServiceController<?> controller) throws UnsupportedOperationException {
        throw readOnlyContext();
    }

    @Override
    public ServiceTarget getServiceTarget() throws UnsupportedOperationException {
        return primaryContext.getServiceTarget();
    }

    @Override
    public CapabilityServiceTarget getCapabilityServiceTarget() throws UnsupportedOperationException {
        return primaryContext.getCapabilityServiceTarget();
    }

    @Override
    public void acquireControllerLock() {
        if (lockStep == null) {
            try {
                controller.acquireWriteLock(operationId, true);
                lockStep = activeStep;
            } catch (InterruptedException e) {
                cancelled = true;
                Thread.currentThread().interrupt();
                throw ControllerLogger.ROOT_LOGGER.operationCancelledAsynchronously();
            }
        }
    }

    @Override
    void releaseStepLocks(Step step) {
        if (step == lockStep) {
            lockStep = null;
            controller.releaseWriteLock(operationId);
        }
    }

    @Override
    public Resource createResource(PathAddress address) throws UnsupportedOperationException {
        throw readOnlyContext();
    }

    @Override
    public void addResource(PathAddress address, Resource toAdd) {
        throw readOnlyContext();
    }

    @Override
    public void addResource(PathAddress address, int index, Resource toAdd) {
        throw readOnlyContext();
    }


    public Resource readResource(final PathAddress requestAddress) {
        return readResource(requestAddress, true);
    }

    public Resource readResource(final PathAddress requestAddress, final boolean recursive) {
        final PathAddress address = activeStep.address.append(requestAddress);
        return readResourceFromRoot(address, recursive);
    }

    public Resource readResourceFromRoot(final PathAddress address) {
        return readResourceFromRoot(address, true);
    }

    public Resource readResourceFromRoot(final PathAddress address, final boolean recursive) {
        return readResourceFromRoot(managementModel, address, recursive);
    }

    @Override
    Resource readResourceFromRoot(ManagementModel model, PathAddress address, boolean recursive) {
        return primaryContext.readResourceFromRoot(model, address, recursive);
    }

    @Override
    public Resource readResourceForUpdate(PathAddress relativeAddress) {
        throw readOnlyContext();
    }

    @Override
    public Resource removeResource(PathAddress relativeAddress) throws UnsupportedOperationException {
        throw readOnlyContext();
    }

    @Override
    public Resource getOriginalRootResource() {
        return primaryContext.getOriginalRootResource();
    }

    @Override
    public boolean isModelAffected() {
        return false;
    }

    @Override
    public boolean isResourceRegistryAffected() {
        return primaryContext.isResourceRegistryAffected();
    }

    @Override
    public boolean isRuntimeAffected() {
        return primaryContext.isRuntimeAffected();
    }

    @Override
    public Stage getCurrentStage() {
        return currentStage;
    }

    @Override
    public void report(MessageSeverity severity, String message) {
        // primaryContext.report(severity, message);
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
        return primaryContext.resolveExpressions(node);
    }

    @Override
    public <T> T getAttachment(AttachmentKey<T> key) {
        if (valueAttachments.containsKey(key)) {
            return key.cast(valueAttachments.get(key));
        }
        return primaryContext.getAttachment(key);
    }

    @Override
    public <V> V attach(final AttachmentKey<V> key, final V value) {
        Assert.checkNotNullParam("key", key);
        return key.cast(valueAttachments.put(key, value));
    }

    @Override
    public <V> V attachIfAbsent(final AttachmentKey<V> key, final V value) {
        Assert.checkNotNullParam("key", key);
        return key.cast(valueAttachments.putIfAbsent(key, value));
    }

    @Override
    public <V> V detach(final AttachmentKey<V> key) {
        Assert.checkNotNullParam("key", key);
        return key.cast(valueAttachments.remove(key));
    }

    @Override
    public AuthorizationResult authorize(ModelNode operation) {
        return primaryContext.authorize(operation);
    }

    @Override
    public AuthorizationResult authorize(ModelNode operation, Set<Action.ActionEffect> effects) {
        return primaryContext.authorize(operation, effects);
    }

    @Override
    public AuthorizationResult authorize(ModelNode operation, String attribute, ModelNode currentValue) {
        return primaryContext.authorize(operation, attribute, currentValue);
    }

    @Override
    public AuthorizationResult authorize(ModelNode operation, String attribute, ModelNode currentValue, Set<Action.ActionEffect> effects) {
        return primaryContext.authorize(operation, attribute, currentValue, effects);
    }

    IllegalStateException readOnlyContext() {
        return ControllerLogger.ROOT_LOGGER.readOnlyContext();
    }

    @Override
    public AuthorizationResult authorizeOperation(ModelNode operation) {
        return primaryContext.authorizeOperation(operation);
    }

    @Override
    public ResourceAuthorization authorizeResource(boolean attributes, boolean isDefaultResource) {
        return primaryContext.authorizeResource(attributes, isDefaultResource);
    }

    @Override
    public void emit(Notification notification) {
        throw readOnlyContext();
    }

    Resource getModel() {
        return primaryContext.getModel();
    }

    @Override
    public void registerCapability(RuntimeCapability capability) {
        throw readOnlyContext();
    }

    @Override
    public void registerAdditionalCapabilityRequirement(String required, String dependent, String attribute) {
        throw readOnlyContext();
    }

    @Override
    public boolean hasOptionalCapability(String required, String dependent, String attribute) {
        throw readOnlyContext();
    }

    @Override
    public void requireOptionalCapability(String required, String dependent, String attribute) {
        throw readOnlyContext();
    }

    @Override
    public void deregisterCapabilityRequirement(String required, String dependent) {
        deregisterCapabilityRequirement(required, dependent, null);
    }

    @Override
    public void deregisterCapabilityRequirement(String required, String dependent, String attribute) {
        throw readOnlyContext();
    }

    @Override
    public void deregisterCapability(String capability) {
        throw readOnlyContext();
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) {
        throw readOnlyContext();
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) {
        throw readOnlyContext();
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityName, Class<?> type) {
        throw readOnlyContext();
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityBaseName, String dynamicPart, Class<?> serviceType) {
        throw readOnlyContext();
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityBaseName, Class<?> serviceType, String... dynamicParts) {
        throw readOnlyContext();
    }

    @Override
    public CapabilityServiceSupport getCapabilityServiceSupport() {
        throw readOnlyContext();
    }
}
