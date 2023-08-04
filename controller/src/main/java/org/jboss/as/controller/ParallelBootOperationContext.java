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

package org.jboss.as.controller;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.ResourceAuthorization;
import org.jboss.as.controller.audit.AuditLogger;
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
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * {@link OperationContext} implementation for parallel handling of subsystem operations during boot.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@SuppressWarnings("deprecation")
class ParallelBootOperationContext extends AbstractOperationContext {

    private final OperationContextImpl primaryContext;
    private final List<ParsedBootOp> runtimeOps;
    private final Thread controllingThread;
    private final int operationId;
    private final ModelControllerImpl controller;

    ParallelBootOperationContext(final ModelController.OperationTransactionControl transactionControl,
                                 final ControlledProcessState processState, final OperationContextImpl primaryContext,
                                 final List<ParsedBootOp> runtimeOps,
                                 final ModelControllerImpl controller, final int operationId, final AuditLogger auditLogger,
                                 final OperationStepHandler extraValidationStepHandler, final Supplier<SecurityIdentity> securityIdentitySupplier) {
        super(primaryContext.getProcessType(), primaryContext.getRunningMode(), transactionControl, processState, true, auditLogger,
                controller.getNotificationSupport(), controller, true, extraValidationStepHandler, null, securityIdentitySupplier);
        this.primaryContext = primaryContext;
        this.runtimeOps = runtimeOps;
        this.controller = controller;
        this.operationId = operationId;
        this.controllingThread = Thread.currentThread();
    }

    void setControllingThread() {
        AbstractOperationContext.controllingThread.set(controllingThread);
    }

    @Override
    public void close() {
        AbstractOperationContext.controllingThread.remove();
        this.lockStep = null;
        super.close();
    }

    @Override
    public void addStep(OperationStepHandler step, Stage stage) throws IllegalArgumentException {
        if (activeStep == null) {
            throw ControllerLogger.ROOT_LOGGER.noActiveStep();
        }
        addStep(activeStep.response, activeStep.operation, step, stage);
    }

    @Override
    public void addStep(ModelNode operation, OperationStepHandler step, Stage stage) throws IllegalArgumentException {
        if (activeStep == null) {
            throw ControllerLogger.ROOT_LOGGER.noActiveStep();
        }
        addStep(activeStep.response, operation, step, stage);
    }

    @Override
    public void addStep(ModelNode operation, OperationStepHandler step, Stage stage, final boolean addFirst) throws IllegalArgumentException {
        if (activeStep == null) {
            throw ControllerLogger.ROOT_LOGGER.noActiveStep();
        }
        addStep(activeStep.response, operation, step, stage, addFirst);
    }

    @Override
    public void addStep(ModelNode response, ModelNode operation, OperationStepHandler step, Stage stage) throws IllegalArgumentException {
        if (stage == Stage.MODEL) {
            super.addStep(response, operation, step, stage);
        } else if (stage == Stage.RUNTIME) {
            if (runtimeOps != null) {
                // Cache for use by the runtime step from ParallelBootOperationStepHandler
                ParsedBootOp parsedOp = new ParsedBootOp(operation, step, response);
                runtimeOps.add(parsedOp);
            } else {
                super.addStep(response, operation, step, stage);
            }
        } else {
            // Handle VERIFY in the primary context, after parallel work is done
            primaryContext.addStep(response, operation, step, stage);
        }
    }

    // Methods unimplemented by superclass

    @Override
    ModelControllerImpl.ManagementModelImpl getManagementModel() {
        throw new IllegalStateException(); // Wrong usage, we cannot guarantee thread safety
    }

    @Override
    boolean isBootOperation() {
        return true;
    }

    @Override
    public InputStream getAttachmentStream(int index) {
        return primaryContext.getAttachmentStream(index);
    }

    @Override
    public int getAttachmentStreamCount() {
        return primaryContext.getAttachmentStreamCount();
    }

    @Override
    public boolean isRollbackOnRuntimeFailure() {
        return primaryContext.isRollbackOnRuntimeFailure();
    }

    @Override
    public boolean isResourceServiceRestartAllowed() {
        return primaryContext.isResourceServiceRestartAllowed();
    }

    @Override
    public ImmutableManagementResourceRegistration getResourceRegistration() {
        ImmutableManagementResourceRegistration parent = primaryContext.getResourceRegistration();
        return  parent.getSubModel(activeStep.address);
    }

    @Override
    public ManagementResourceRegistration getResourceRegistrationForUpdate() {
        acquireControllerLock();
        ManagementResourceRegistration parent = primaryContext.getResourceRegistrationForUpdate();
        return  parent.getSubModel(activeStep.address);
    }

    @Override
    public ImmutableManagementResourceRegistration getRootResourceRegistration() {
        return primaryContext.getRootResourceRegistration();
    }

    @Override
    public ServiceRegistry getServiceRegistry(boolean modify) throws UnsupportedOperationException {
        if(modify) {
            acquireControllerLock();
        }
        return primaryContext.getServiceRegistry(modify, activeStep);
    }

    @Override
    public ServiceController<?> removeService(ServiceName name) throws UnsupportedOperationException {
        acquireControllerLock();
        return primaryContext.removeService(name);
    }

    @Override
    public void removeService(ServiceController<?> controller) throws UnsupportedOperationException {
        acquireControllerLock();
        primaryContext.removeService(controller);
    }

    @Override
    public CapabilityServiceTarget getServiceTarget() throws UnsupportedOperationException {
        return getCapabilityServiceTarget();
    }

    @Override
    public CapabilityServiceTarget getCapabilityServiceTarget() throws UnsupportedOperationException {
        acquireControllerLock();
        return primaryContext.getServiceTarget(activeStep);
    }

    @Override
    public void acquireControllerLock() {
        if(lockStep == null) {
            try {
                controller.acquireWriteLock(operationId, true);
                recordWriteLock();
            } catch (InterruptedException e) {
                cancelled = true;
                Thread.currentThread().interrupt();
                throw ControllerLogger.ROOT_LOGGER.operationCancelledAsynchronously();
            }
        }
    }

    @Override
    public Resource createResource(PathAddress address) throws UnsupportedOperationException {
        acquireControllerLock();
        PathAddress fullAddress = activeStep.address.append(address);
        return primaryContext.createResource(fullAddress);
    }

    @Override
    public void addResource(PathAddress address, Resource toAdd) {
        acquireControllerLock();
        PathAddress fullAddress = activeStep.address.append(address);
        primaryContext.addResource(fullAddress, toAdd);
    }

    @Override
    public void addResource(PathAddress address, int index, Resource toAdd) {
        acquireControllerLock();
        PathAddress fullAddress = activeStep.address.append(address);
        primaryContext.addResource(fullAddress, index, toAdd);
    }


    @Override
    public Resource readResource(PathAddress address) {
        return readResource(address, true);
    }

    @Override
    public Resource readResource(PathAddress address, boolean recursive) {
        PathAddress fullAddress = activeStep.address.append(address);
        return primaryContext.readResource(fullAddress, recursive);
    }

    @Override
    public Resource readResourceFromRoot(PathAddress address) {
        return readResourceFromRoot(address, true);
    }

    @Override
    public Resource readResourceFromRoot(PathAddress address, boolean recursive) {
        return primaryContext.readResourceFromRoot(address, recursive);
    }

    @Override
    protected Resource readResourceFromRoot(ManagementModel model, PathAddress address, boolean recursive) {
        return primaryContext.readResourceFromRoot(model, address, recursive);
    }

    @Override
    public Resource readResourceForUpdate(PathAddress address) {
        acquireControllerLock();
        PathAddress fullAddress = activeStep.address.append(address);
        return primaryContext.readResourceForUpdate(fullAddress);
    }

    @Override
    public Resource removeResource(PathAddress address) throws UnsupportedOperationException {
        acquireControllerLock();
        PathAddress fullAddress = activeStep.address.append(address);
        return primaryContext.removeResource(fullAddress);
    }

    @Override
    public Resource getOriginalRootResource() {
        return primaryContext.getOriginalRootResource();
    }

    @Override
    public boolean isModelAffected() {
        return primaryContext.isModelAffected();
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
        return primaryContext.getCurrentStage();
    }

    @Override
    public void report(MessageSeverity severity, String message) {
        primaryContext.report(severity, message);
    }

    @Override
    public boolean markResourceRestarted(PathAddress resource, Object owner) {
        throw new UnsupportedOperationException("Resource restarting is not supported during boot");
    }

    @Override
    public boolean revertResourceRestarted(PathAddress resource, Object owner) {
        throw new UnsupportedOperationException("Resource restarting is not supported during boot");
    }

    @Override
    void awaitServiceContainerStability() throws InterruptedException {
        // ignored
    }

    @Override
    ConfigurationPersister.PersistenceResource createPersistenceResource() throws ConfigurationPersistenceException {
        // We don't persist
        return null;
    }

    @Override
    void operationRollingBack() {
        // BES 2015/06/30 hmm. telling the primary context to discarding the management model
        // here will screw up other parallel contexts that are still running. but if we don't,
        // during rollback our OSHs will still see the changes made to the model.
        // Oh well, I'm not going to worry about it as this runs in boot, and a rollback in
        // boot should just result in the whole process going away anyway.
    }

    @Override
    public void emit(Notification notification) {
        primaryContext.emit(notification);
    }

    @Override
    public void registerCapability(RuntimeCapability capability) {
        // pass in the step we are executing so it can be failed if there is problem resolving capabilities/requirements
        primaryContext.registerCapability(capability, activeStep, null);
    }

    @Override
    public void registerAdditionalCapabilityRequirement(String required, String dependent, String attribute) {
        // pass in the step we are executing so it can be failed if there is problem resolving capabilities/requirements
        primaryContext.registerAdditionalCapabilityRequirement(required, dependent, activeStep, attribute);
    }

    @Override
    public boolean hasOptionalCapability(String required, String dependent, String attribute) {
        // pass in the step we are executing so it can be failed if there is problem resolving capabilities/requirements
        return primaryContext.requestOptionalCapability(required, dependent, true, activeStep, attribute);
    }

    @Override
    public void requireOptionalCapability(String required, String dependent, String attribute) throws OperationFailedException {
        // pass in the step we are executing so it can be failed if there is problem resolving capabilities/requirements
        primaryContext.requireOptionalCapability(required, dependent, activeStep, attribute);
    }

    @Override
    public void deregisterCapabilityRequirement(String required, String dependent) {
        deregisterCapabilityRequirement(required, dependent, null);
    }

    @Override
    public void deregisterCapabilityRequirement(String required, String dependent, String attribute) {
        // pass in the step we are executing so it can be failed if there is problem resolving capabilities/requirements
        primaryContext.removeCapabilityRequirement(required, dependent, activeStep, attribute);
    }

    @Override
    public void deregisterCapability(String capability) {
        // pass in the step we are executing so it can be failed if there is problem resolving capabilities/requirements
        primaryContext.removeCapability(capability, activeStep);
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) {
        return primaryContext.getCapabilityRuntimeAPI(capabilityName, apiType, activeStep);
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) {
        return primaryContext.getCapabilityRuntimeAPI(RuntimeCapability.buildDynamicCapabilityName(capabilityBaseName, dynamicPart), apiType, activeStep);
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityName, Class<?> type) {
        return primaryContext.getCapabilityServiceName(capabilityName, type, activeStep.address);
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityBaseName, Class<?> serviceType, String... dynamicParts) {
        return primaryContext.getCapabilityServiceName(capabilityBaseName, serviceType, dynamicParts);
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityBaseName, String dynamicPart, Class<?> serviceType) {
        return primaryContext.getCapabilityServiceName(capabilityBaseName, dynamicPart, serviceType);
    }

    @Override
    public CapabilityServiceSupport getCapabilityServiceSupport() {
        return primaryContext.getCapabilityServiceSupport();
    }

    @Override
    void releaseStepLocks(AbstractStep step) {
        if(step.matches(lockStep)) {
            controller.releaseWriteLock(operationId);
            lockStep = null;
        }
    }

    @Override
    void waitForRemovals() {
        // nothing to do
    }

    @Override
    boolean isReadOnly() {
        return primaryContext.isReadOnly();
    }

    @Override
    ManagementResourceRegistration getRootResourceRegistrationForUpdate() {
        return primaryContext.getRootResourceRegistrationForUpdate();
    }

    @Override
    public ModelNode resolveExpressions(ModelNode node) throws OperationFailedException {
        return primaryContext.resolveExpressions(node);
    }

    @Override
    public <T> T getAttachment(final AttachmentKey<T> key) {
        return primaryContext.getAttachment(key);
    }

    @Override
    public <T> T attach(final AttachmentKey<T> key, final T value) {
        return primaryContext.attach(key, value);
    }

    @Override
    public <T> T attachIfAbsent(final AttachmentKey<T> key, final T value) {
        return primaryContext.attachIfAbsent(key, value);
    }

    @Override
    public <T> T detach(final AttachmentKey<T> key) {
        return primaryContext.detach(key);
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

    @Override
    public AuthorizationResult authorizeOperation(ModelNode operation) {
        return primaryContext.authorizeOperation(operation);
    }

    @Override
    public ResourceAuthorization authorizeResource(boolean attributes, boolean isDefaultResource) {
        return primaryContext.authorizeResource(attributes, isDefaultResource);
    }

    Resource getModel() {
        return primaryContext.getModel();
    }

    @Override
    void logAuditRecord() {
        // handled by the primary context
    }
}
