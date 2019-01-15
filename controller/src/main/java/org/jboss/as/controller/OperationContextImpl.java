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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACTIVE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_THREAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_ROLLOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCLUSIVE_RUNNING_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTION_STATUS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOURCE_ADDED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOURCE_REMOVED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_MODIFICATION_BEGUN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_MODIFICATION_COMPLETE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.jboss.as.controller._private.OperationFailedRuntimeException;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.Action.ActionEffect;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.AuthorizationResult.Decision;
import org.jboss.as.controller.access.Caller;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.ResourceAuthorization;
import org.jboss.as.controller.access.ResourceNotAddressableException;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityId;
import org.jboss.as.controller.capability.registry.CapabilityResolutionContext;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RuntimeRequirementRegistration;
import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationSupport;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.global.ReadResourceHandler;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.DelegatingImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.ContextAttachments;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.DelegatingServiceBuilder;
import org.jboss.msc.service.DelegatingServiceController;
import org.jboss.msc.service.DelegatingServiceRegistry;
import org.jboss.msc.service.DelegatingServiceTarget;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.msc.value.Value;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Operation context implementation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class OperationContextImpl extends AbstractOperationContext {

    private static final Object NULL = new Object();

    private static final Set<Action.ActionEffect> ADDRESS = EnumSet.of(Action.ActionEffect.ADDRESS);
    private static final Set<Action.ActionEffect> READ_CONFIG = EnumSet.of(Action.ActionEffect.READ_CONFIG);
    private static final Set<Action.ActionEffect> READ_RUNTIME = EnumSet.of(Action.ActionEffect.READ_RUNTIME);
    private static final Set<Action.ActionEffect> READ_WRITE_CONFIG = EnumSet.of(Action.ActionEffect.READ_CONFIG, Action.ActionEffect.WRITE_CONFIG);
    private static final Set<Action.ActionEffect> READ_WRITE_RUNTIME = EnumSet.of(Action.ActionEffect.READ_RUNTIME, Action.ActionEffect.WRITE_RUNTIME);
    private static final Set<Action.ActionEffect> WRITE_CONFIG = EnumSet.of(Action.ActionEffect.WRITE_CONFIG);
    private static final Set<Action.ActionEffect> WRITE_RUNTIME = EnumSet.of(Action.ActionEffect.WRITE_RUNTIME);
    private static final Set<Action.ActionEffect> ALL_READ_WRITE = EnumSet.of(Action.ActionEffect.READ_CONFIG, Action.ActionEffect.READ_RUNTIME, Action.ActionEffect.WRITE_CONFIG, Action.ActionEffect.WRITE_RUNTIME);

    private final ModelControllerImpl modelController;
    private final OperationMessageHandler messageHandler;
    private final Map<ServiceName, ServiceController<?>> realRemovingControllers = new HashMap<>();
    // protected by "realRemovingControllers"
    private final Map<ServiceName, Step> removalSteps = new HashMap<>();
    private final OperationAttachments attachments;
    /** Tracks the addresses associated with writes to the model.
     * We use a map with dummy values just to take advantage of ConcurrentHashMap  */
    private final Map<PathAddress, Object> affectsModel;
    /** Resources that have had their services restarted, used by ALLOW_RESOURCE_SERVICE_RESTART This should be confined to a thread, so no sync needed */
    private Map<PathAddress, Object> restartedResources = Collections.emptyMap();
    private final ContextAttachments contextAttachments = new ContextAttachments();
    private final Map<OperationId, AuthorizationResponseImpl> authorizations = new ConcurrentHashMap<>();
    private final Set<ContextServiceTarget> serviceTargets = Collections.synchronizedSet(new HashSet<>());
    private final Set<OperationContextServiceRegistry> serviceRegistries = Collections.synchronizedSet(new HashSet<>());
    private volatile BlockingTimeout blockingTimeout;
    private final long startTime = System.nanoTime();
    private volatile long exclusiveStartTime = -1;

    /** Tracks whether any steps have gotten write access to  the resource tree */
    private volatile boolean affectsResourceTree;
    /** Tracks whether any steps have gotten write access to the management resource registration*/
    private volatile boolean affectsResourceRegistration;
    /** Tracks whether any steps have gotten write access to the capability registry */
    private volatile boolean affectsCapabilityRegistry;

    private volatile ModelControllerImpl.ManagementModelImpl managementModel;

    private final ModelControllerImpl.ManagementModelImpl originalModel;

    /** Tracks the relationship between domain resources and hosts and server groups */
    private volatile HostServerGroupTracker hostServerGroupTracker;

    /** Tracks whether any steps have gotten write access to the runtime */
    private volatile boolean affectsRuntime;
    /** The step that acquired the write lock */
    private Step lockStep;
    /** The step that acquired the container monitor  */
    private Step containerMonitorStep;
    private boolean notifiedModificationBegun;
    private volatile Boolean requiresModelUpdateAuthorization;
    private volatile boolean readOnly = true;

    /** Associates a requirement for a capability with the step that added it */
    private final ConcurrentMap<RuntimeRequirementRegistration, Set<Step>> addedRequirements = new ConcurrentHashMap<>();
    /** Associates a removed capability with the step that removed it */
    private final ConcurrentMap<CapabilityId, Step> removedCapabilities = new ConcurrentHashMap<>();

    private final Integer operationId;
    private final String operationName;
    private final ModelNode operationAddress;
    private final AccessAuditContext accessAuditContext;
    private final ActiveOperationResource activeOperationResource;
    private final BooleanHolder done = new BooleanHolder();
    private final boolean capabilitiesAlreadyBroken;
    private final boolean partialModel;

    private volatile ExecutionStatus executionStatus = ExecutionStatus.EXECUTING;


    OperationContextImpl(final Integer operationId,
                         final String operationName, final ModelNode operationAddress,
                         final ModelControllerImpl modelController, final ProcessType processType,
                         final RunningMode runningMode,
                         final OperationHeaders operationHeaders,
                         final OperationMessageHandler messageHandler, final OperationAttachments attachments,
                         final ModelControllerImpl.ManagementModelImpl managementModel, final ModelController.OperationTransactionControl transactionControl,
                         final ControlledProcessState processState, final AuditLogger auditLogger, final boolean booting,
                         final HostServerGroupTracker hostServerGroupTracker,
                         final AccessAuditContext accessAuditContext,
                         final NotificationSupport notificationSupport,
                         final boolean skipModelValidation,
                         final OperationStepHandler extraValidationStepHandler,
                         final boolean partialModel,
                         final Supplier<SecurityIdentity> securityIdentitySupplier) {
        super(processType, runningMode, transactionControl, processState, booting, auditLogger, notificationSupport,
                modelController, skipModelValidation, extraValidationStepHandler, operationHeaders, securityIdentitySupplier);
        this.operationId = operationId;
        this.operationName = operationName;
        this.operationAddress = operationAddress.isDefined()
                ? operationAddress : ModelControllerImpl.EMPTY_ADDRESS;
        this.managementModel = managementModel;
        this.originalModel = managementModel;
        this.modelController = modelController;
        this.messageHandler = messageHandler;
        this.attachments = attachments;
        this.affectsModel = booting ? new ConcurrentHashMap<>(16 * 16) : new HashMap<>(1);
        this.hostServerGroupTracker = hostServerGroupTracker;
        this.activeOperationResource = new ActiveOperationResource();
        this.accessAuditContext = accessAuditContext;
        this.partialModel = partialModel;
        if(runningMode == RunningMode.ADMIN_ONLY) {
            boolean hostXmlOnly = booting && !processType.isServer() && partialModel;
            CapabilityRegistry.CapabilityValidation validation = managementModel.validateCapabilityRegistry(true, hostXmlOnly);
            this.capabilitiesAlreadyBroken = !validation.isValid();
        } else {
            this.capabilitiesAlreadyBroken = false;
        }
    }

    public InputStream getAttachmentStream(final int index) {
        if (attachments == null) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return attachments.getInputStreams().get(index);
    }

    public int getAttachmentStreamCount() {
        return attachments == null ? 0 : attachments.getInputStreams().size();
    }

    @Override
    boolean stageCompleted(Stage stage) {
        return (stage != Stage.MODEL || validateCapabilities());
    }

    ModelControllerImpl.ManagementModelImpl getManagementModel() {
        return managementModel;
    }

    private boolean validateCapabilities() {

        if (! (affectsResourceTree || affectsCapabilityRegistry || affectsResourceRegistration || affectsRuntime)) {
            return true;
        }

        // Validate that all required capabilities are available and fail any steps that broke this
        boolean hostXmlOnly = !getProcessType().isServer() && partialModel;
        CapabilityRegistry.CapabilityValidation validation = managementModel.validateCapabilityRegistry(false, hostXmlOnly);
        boolean ok = validation.isValid();
        final boolean adminOnly = this.getRunningMode() == RunningMode.ADMIN_ONLY;
        //if we are in admin only mode and everything is already broken then we don't care about failures
        final boolean ignoreFailures = adminOnly && (capabilitiesAlreadyBroken || isBooting());
        if (!ok) {

            boolean failureRecorded = false;
            StringBuilder unexplainedProblem = null;

            // Whether we care about context depends on whether we are a server
            boolean ignoreContext = getProcessType().isServer();
            CapabilityResolutionContext resolutionContext = validation.getCapabilityResolutionContext();
            Map<CapabilityId, Set<RuntimeRequirementRegistration>> missing = validation.getMissingRequirements();
            Map<Step, Set<CapabilityId>> missingForStep = new HashMap<>();
            for (Map.Entry<CapabilityId, Set<RuntimeRequirementRegistration>> entry : missing.entrySet()) {
                CapabilityId required = entry.getKey();
                // See if any step removed this capability
                Step guilty = findCapabilityRemovalStep(required, ignoreContext, resolutionContext);
                if (guilty != null) {
                    // Change the response for the step to mark as failed
                    StringBuilder msg = new StringBuilder();
                    if (ignoreContext) {
                        msg = msg.append(ControllerLogger.ROOT_LOGGER.cannotRemoveRequiredCapability(required.getName()));
                    } else {
                        msg = msg.append(ControllerLogger.ROOT_LOGGER.cannotRemoveRequiredCapabilityInContext(required.getName(), required.getScope().getName()));
                    }
                    for (RuntimeRequirementRegistration reg : entry.getValue()) {
                        RegistrationPoint rp = reg.getOldestRegistrationPoint();
                        if (rp.getAttribute() == null) {
                            msg = msg.append('\n').append(ControllerLogger.ROOT_LOGGER.requirementPointSimple(reg.getDependentName(), rp.getAddress().toCLIStyleString()));
                        } else {
                            msg = msg.append('\n').append(ControllerLogger.ROOT_LOGGER.requirementPointFull(reg.getDependentName(), rp.getAttribute(), rp.getAddress().toCLIStyleString()));
                        }
                    }
                    String msgString = msg.toString();
                    if(!ignoreFailures) {
                        guilty.response.get(FAILURE_DESCRIPTION).set(msgString);
                    }
                    failureRecorded = true;
                    if (isBooting() || ignoreFailures) { // this is unlikely for this block since boot wouldn't remove, but let's be thorough.
                        ControllerLogger.ROOT_LOGGER.error(guilty.address.toCLIStyleString() + " -- " + msgString);
                    }
                } else {
                    for (RuntimeRequirementRegistration reqReq : entry.getValue()) {
                        // Problem wasn't a capability removal.
                        // See what step(s) added this requirement
                        Set<Step> bereft = addedRequirements.get(reqReq);
                        if (bereft != null && bereft.size() > 0) {
                            for (Step step : bereft) {
                                Set<CapabilityId> set = missingForStep.get(step);
                                if (set == null) {
                                    set = new HashSet<>();
                                    missingForStep.put(step, set);
                                }
                                set.add(required);
                            }
                        } else {
                            if (unexplainedProblem == null) {
                                unexplainedProblem = new StringBuilder(ControllerLogger.ROOT_LOGGER.requiredCapabilityMissing());
                            }
                            String formattedCapability = ignoreContext
                                    ? ControllerLogger.ROOT_LOGGER.formattedCapabilityName(reqReq.getRequiredName())
                                    : ControllerLogger.ROOT_LOGGER.formattedCapabilityId(reqReq.getRequiredName(), reqReq.getDependentContext().getName());

                            Set<PathAddress> possiblePoints = managementModel.getCapabilityRegistry().getPossibleProviderPoints(reqReq.getDependentId());
                            appendPossibleProviderPoints(unexplainedProblem, formattedCapability, possiblePoints);
                        }
                    }
                }
            }
            // Change the response for all steps that added an unfulfilled requirement
            for (Map.Entry<Step, Set<CapabilityId>> entry : missingForStep.entrySet()) {
                Step step = entry.getKey();
                ModelNode response = step.response;
                // only overwrite reponse failure-description if there isn't one
                StringBuilder msg = response.hasDefined(FAILURE_DESCRIPTION)
                        ? null
                        : new StringBuilder(ControllerLogger.ROOT_LOGGER.requiredCapabilityMissing());
                StringBuilder bootMsg = isBooting() || ignoreFailures
                        ? new StringBuilder(ControllerLogger.ROOT_LOGGER.requiredCapabilityMissing(step.address.toCLIStyleString()))
                        : null;
                for (CapabilityId id : entry.getValue()) {
                    String formattedCapability = ignoreContext
                            ? ControllerLogger.ROOT_LOGGER.formattedCapabilityName(id.getName())
                            : ControllerLogger.ROOT_LOGGER.formattedCapabilityId(id.getName(), id.getScope().getName());
                    Set<PathAddress> possiblePoints = managementModel.getCapabilityRegistry().getPossibleProviderPoints(id);
                    if (msg != null) {
                        msg = appendPossibleProviderPoints(msg, formattedCapability, possiblePoints);
                    }
                    if (bootMsg != null) {
                        bootMsg = appendPossibleProviderPoints(bootMsg, formattedCapability, possiblePoints);
                    }
                }
                if (msg != null) {
                    if(!ignoreFailures) {
                        response.get(FAILURE_DESCRIPTION).set(msg.toString());
                    }
                    failureRecorded = true;
                }
                if (bootMsg != null) {
                    ControllerLogger.ROOT_LOGGER.error(bootMsg.toString());
                }
            }

            if (!ignoreContext) {

                for (RuntimeRequirementRegistration reg : validation.getInconsistentRequirements()) {
                    // See what step(s) added this requirement
                    Set<Step> inconsistent = addedRequirements.get(reg);
                    if (inconsistent != null && inconsistent.size() > 0) {
                        for (Step step : inconsistent) {
                            ModelNode response = step.response;
                            String depConName = reg.getDependentContext().getName();
                            // only overwrite reponse failure-description if there isn't one
                            if (!response.hasDefined(FAILURE_DESCRIPTION)) {
                                if(!(adminOnly && isBooting())) {
                                    response.get(FAILURE_DESCRIPTION).set(ControllerLogger.ROOT_LOGGER.inconsistentCapabilityContexts(reg.getRequiredName(),
                                            reg.getDependentName(), depConName, depConName));
                                }
                                failureRecorded = true;
                            }
                            if (isBooting()) {
                                ControllerLogger.ROOT_LOGGER.inconsistentCapabilityContexts(reg.getDependentName(),
                                        depConName, step.address.toCLIStyleString(), reg.getRequiredName(), depConName);
                            }
                        }
                    } else {
                        if (unexplainedProblem == null) {
                            unexplainedProblem = new StringBuilder();
                        } else {
                            unexplainedProblem.append('\n');
                        }
                        String depConName = reg.getDependentContext().getName();
                        unexplainedProblem.append(ControllerLogger.ROOT_LOGGER.inconsistentCapabilityContexts(reg.getRequiredName(),
                                reg.getDependentName(), depConName, depConName));
                    }
                }

            }

            // If we didn't record a failure on any response, put one on the initial response
            if (!failureRecorded && unexplainedProblem != null && !initialResponse.hasDefined(FAILURE_DESCRIPTION)) {

                if(!ignoreFailures) {
                    initialResponse.get(FAILURE_DESCRIPTION).set(unexplainedProblem.toString());
                }
                if(isBooting() || ignoreFailures) {
                    ControllerLogger.ROOT_LOGGER.error(unexplainedProblem);
                }
            }
        }
        return ok || ignoreFailures;

    }
    private static StringBuilder appendPossibleProviderPoints(StringBuilder sb, String formattedCapability, Set<PathAddress> possible){
        //"you wanted X and it doesn't exist; here's where you can add X"
        sb = sb.append(System.lineSeparator()).append(formattedCapability);
        if (possible.isEmpty()){
            return sb.append(ControllerLogger.ROOT_LOGGER.noKnownProviderPoints());
        }
        StringBuffer points = new StringBuffer();
        for (PathAddress c: possible){
            points = points.append(System.lineSeparator()).append("\t\t").append(c.toCLIStyleString());
        }
        return sb.append(ControllerLogger.ROOT_LOGGER.possibleCapabilityProviderPoints(points.toString()));
    }

    private Step findCapabilityRemovalStep(CapabilityId missingRequirement, boolean ignoreContext, CapabilityResolutionContext resolutionContext) {
        Step result = removedCapabilities.get(missingRequirement);
        if (result == null && !ignoreContext) {
            String missingName = missingRequirement.getName();
            for (Map.Entry<CapabilityId, Step> entry : removedCapabilities.entrySet()) {
                CapabilityId removedId = entry.getKey();
                if (missingName.equals(removedId.getName())
                        && removedId.getScope().canSatisfyRequirement(missingRequirement.getName(), missingRequirement.getScope(), resolutionContext)) {
                    result = entry.getValue();
                    break;
                }
            }
        }
        return result;
    }

    @Override
    void awaitServiceContainerStability() throws InterruptedException, TimeoutException {
        if (affectsRuntime) {
            MGMT_OP_LOGGER.debugf("Entered VERIFY stage; waiting for service container to settle");
            long timeout = getBlockingTimeout().getLocalBlockingTimeout();
            ExecutionStatus originalExecutionStatus = executionStatus;
            try {
                // First wait until any removals we've initiated have begun processing, otherwise
                // the ContainerStateMonitor may not have gotten the notification causing it to untick
                executionStatus = ExecutionStatus.AWAITING_STABILITY;
                waitForRemovals();
                ContainerStateMonitor.ContainerStateChangeReport changeReport =
                        modelController.awaitContainerStateChangeReport(timeout, TimeUnit.MILLISECONDS);
                if (changeReport != null && changeReport.hasNewProblems()) {
                    // If any services are missing, add a verification handler to see if we caused it
                    if (!changeReport.getMissingServices().isEmpty()) {
                        ServiceRemovalVerificationHandler removalVerificationHandler = new ServiceRemovalVerificationHandler(changeReport);
                        addStep(new ModelNode(), new ModelNode(), PathAddress.EMPTY_ADDRESS, removalVerificationHandler, Stage.VERIFY);
                    }
                    // Add a step to report a failure if it looks as if no other verification has done so.
                    // This handler will report directly to the initial response as its reporting means
                    // we don't know what exact step (e.g. in a composite) triggered the problem
                    ContainerStateVerificationHandler csvh = new ContainerStateVerificationHandler(changeReport);
                    addStep(initialResponse, initialOperation, PathAddress.EMPTY_ADDRESS, csvh, Stage.VERIFY);
                }

            } catch (TimeoutException te) {
                getBlockingTimeout().timeoutDetected();
                // Deliberate log and throw; we want to log this but the caller method passes a slightly different
                // message to the user as part of the operation response
                MGMT_OP_LOGGER.timeoutExecutingOperation(timeout / 1000, containerMonitorStep.operationId.name, containerMonitorStep.address);
                throw te;
            } finally {
                executionStatus = originalExecutionStatus;
                notifyModificationsComplete();
            }
        }
    }

    private void notifyModificationsComplete() {
        if (notifiedModificationBegun) {
            Notification notification = new Notification(RUNTIME_MODIFICATION_COMPLETE,
                    modelController.getModelControllerResourceAddress(managementModel),
                    MGMT_OP_LOGGER.runtimeModificationComplete());
            notificationSupport.emit(notification);
            notifiedModificationBegun = false;
        }
    }

    @Override
    protected void waitForRemovals() throws InterruptedException, TimeoutException {
        if (affectsRuntime && !cancelled) {
            synchronized (realRemovingControllers) {
                long waitTime = getBlockingTimeout().getLocalBlockingTimeout();
                long end = System.currentTimeMillis() + waitTime;
                boolean wait = !realRemovingControllers.isEmpty() && !cancelled;
                while (wait && waitTime > 0) {
                    realRemovingControllers.wait(waitTime);
                    wait = !realRemovingControllers.isEmpty() && !cancelled;
                    waitTime = end - System.currentTimeMillis();
                }

                if (wait) {
                    getBlockingTimeout().timeoutDetected();
                    throw new TimeoutException();
                }
            }
        }
    }

    @Override
    ConfigurationPersister.PersistenceResource createPersistenceResource() throws ConfigurationPersistenceException {
        return
            (affectsResourceTree || affectsCapabilityRegistry || affectsResourceRegistration)
                ? modelController.writeModel(managementModel, affectsModel.keySet(), affectsResourceTree,
                    affectsCapabilityRegistry, affectsResourceRegistration)
                : null;
    }

    @Override
    void operationRollingBack() {
        modelController.discardModel(managementModel, affectsResourceTree, affectsCapabilityRegistry, affectsResourceRegistration);
    }

    @Override
    public boolean isRollbackOnRuntimeFailure() {
        return operationHeaders.getContextFlags().contains(ContextFlag.ROLLBACK_ON_FAIL);
    }

    @Override
    public boolean isResourceServiceRestartAllowed() {
        return operationHeaders.getContextFlags().contains(ContextFlag.ALLOW_RESOURCE_SERVICE_RESTART);
    }

    public ManagementResourceRegistration getResourceRegistrationForUpdate() {
        return getMutableResourceRegistration(activeStep.address);
    }

    private ManagementResourceRegistration getMutableResourceRegistration(PathAddress absoluteAddress) {

        readOnly = false;

        assert isControllingThread();
        // Don't require model as some handlers do runtime registration due to needing to see
        // what's exposed by a runtime service
        //checkStageModel(currentStage);
        assertNotComplete(currentStage);

        authorize(false, READ_WRITE_CONFIG);
        ensureLocalManagementResourceRegistration();
        ManagementResourceRegistration mrr =  managementModel.getRootResourceRegistration();
        return absoluteAddress == null ? mrr : mrr.getSubModel(absoluteAddress);
    }

    @Override
    public ImmutableManagementResourceRegistration getResourceRegistration() {
        assert isControllingThread();
        assertNotComplete(currentStage);
        authorize(false, Collections.<ActionEffect>emptySet());
        ManagementResourceRegistration delegate = activeStep.getManagementResourceRegistration(managementModel);
        return delegate == null ? null : new DelegatingImmutableManagementResourceRegistration(delegate);
    }

    @Override
    public ImmutableManagementResourceRegistration getRootResourceRegistration() {
        assert isControllingThread();
        assertNotComplete(currentStage);
        ImmutableManagementResourceRegistration delegate = managementModel.getRootResourceRegistration();
        return delegate == null ? null : new DelegatingImmutableManagementResourceRegistration(delegate);
    }

    @Override
    public ServiceRegistry getServiceRegistry(final boolean modify) throws UnsupportedOperationException {
        return getServiceRegistry(modify, this.activeStep);
    }

    /**
     * Gets a service registry that will ensure
     * {@link org.jboss.msc.service.ServiceController#setMode(org.jboss.msc.service.ServiceController.Mode) changes to the mode}
     * of registered services will result in subsequent verification of service stability.
     *
     * @param modify {@code true} if the caller intends to modify state
     * @param registryActiveStep the {@link org.jboss.as.controller.AbstractOperationContext.Step} that encapsulates
     *                           the {@link org.jboss.as.controller.OperationStepHandler} that is making the call.
     * @return the service registry
     */
    ServiceRegistry getServiceRegistry(final boolean modify, final Step registryActiveStep) {
        if (modify) {
            readOnly = false;
        }

        assert isControllingThread();

        Stage currentStage = this.currentStage;
        if (modify) {
            if (!isRuntimeChangeAllowed()) {
                throw ControllerLogger.ROOT_LOGGER.serviceRegistryRuntimeOperationsOnly();
            }
        } else {
            assertNotComplete(currentStage);
        }
        authorize(false, modify ? READ_WRITE_RUNTIME : READ_RUNTIME);
        if (modify) {
            ensureWriteLockForRuntime();
        }
        OperationContextServiceRegistry registry = new OperationContextServiceRegistry(modelController.getServiceRegistry(), registryActiveStep);
        serviceRegistries.add(registry);
        return registry;
    }

    public ServiceController<?> removeService(final ServiceName name) throws UnsupportedOperationException {

        readOnly = false;

        assert isControllingThread();

        if (!isRuntimeChangeAllowed()) {
            throw ControllerLogger.ROOT_LOGGER.serviceRemovalRuntimeOperationsOnly();
        }
        authorize(false, WRITE_RUNTIME);
        ensureWriteLockForRuntime();
        ServiceController<?> controller = modelController.getServiceRegistry().getService(name);
        if (controller != null) {
            doRemove(controller);
        }
        return controller;
    }

    @Override
    public boolean markResourceRestarted(PathAddress resource, Object owner) {
        if (restartedResources.containsKey(resource) ) {
            return false;
        }

        if (restartedResources == Collections.EMPTY_MAP) {
            restartedResources = new HashMap<PathAddress, Object>();
        }

        restartedResources.put(resource, owner);

        return true;
    }

    @Override
    public boolean revertResourceRestarted(PathAddress resource, Object owner) {
        if (restartedResources.get(resource) == owner) {
            restartedResources.remove(resource);
            return true;
        }

        return false;
    }

    @Override
    public void removeService(final ServiceController<?> controller) throws UnsupportedOperationException {

        readOnly = false;

        assert isControllingThread();

        if (!isRuntimeChangeAllowed()) {
            throw ControllerLogger.ROOT_LOGGER.serviceRemovalRuntimeOperationsOnly();
        }
        authorize(false, WRITE_RUNTIME);
        ensureWriteLockForRuntime();
        if (controller != null) {
            doRemove(controller);
        }
    }

    private void doRemove(final ServiceController<?> controller) {
        final Step removalStep = activeStep;
        removalStep.hasRemovals = true;
        final UninterruptibleCountDownLatch latch = new UninterruptibleCountDownLatch(1);
        controller.addListener(new LifecycleListener() {
            public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                latch.awaitUninterruptibly();
                if (event == LifecycleEvent.REMOVED) {
                    synchronized (realRemovingControllers) {
                        ServiceName name = controller.getName();
                        if (realRemovingControllers.get(name) == controller) {
                            realRemovingControllers.remove(name);
                            removalSteps.put(name, removalStep);
                            realRemovingControllers.notifyAll();
                        }
                    }
                }
            }
        });
        try {
            final ServiceController<?> realController = unwrap(controller);
            synchronized (realRemovingControllers) {
                realRemovingControllers.put(controller.getName(), realController);
                realController.setMode(ServiceController.Mode.REMOVE);
            }
        } finally {
            latch.countDown();
        }
    }

    private ServiceController unwrap(final ServiceController<?> controller) {
        if (controller instanceof OperationContextServiceController) {
            return ((OperationContextServiceController) controller).getDelegate();
        } else return controller;
    }

    @Override
    public ServiceTarget getServiceTarget() throws UnsupportedOperationException {
        return getCapabilityServiceTarget();
    }

    @Override
    public CapabilityServiceTarget getCapabilityServiceTarget() throws UnsupportedOperationException {
        return getServiceTarget(activeStep);
    }

    /**
     * Gets a service target that will ensure that any
     * {@link org.jboss.msc.service.ServiceTarget#addService(org.jboss.msc.service.ServiceName, org.jboss.msc.service.Service)
     * added services} will be tracked for subsequent verification of service stability.
     *
     * @param targetActiveStep the {@link org.jboss.as.controller.AbstractOperationContext.Step} that encapsulates
     *                           the {@link org.jboss.as.controller.OperationStepHandler} that is making the call.
     * @return the service target
     */
    CapabilityServiceTarget getServiceTarget(final Step targetActiveStep) throws UnsupportedOperationException {

        readOnly = false;

        assert isControllingThread();

        if (!isRuntimeChangeAllowed()) {
            throw ControllerLogger.ROOT_LOGGER.serviceTargetRuntimeOperationsOnly();
        }
        ensureWriteLockForRuntime();

        final ContextServiceBuilderSupplier supplier = new ContextServiceBuilderSupplier() {
            @Override
            public <T> ContextServiceBuilder<T> getContextServiceBuilder(ServiceBuilder<T> delegate, final ServiceName name) {

                final ContextServiceInstaller csi = new ContextServiceInstaller() {

                    @Override
                    public <X> ServiceController<X> installService(ServiceBuilder<X> realBuilder) {
                        return OperationContextImpl.this.installService(realBuilder, name, targetActiveStep);
                    }

                    @Override
                    public ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType, PathAddress address) {
                        return OperationContextImpl.this.getCapabilityServiceName(capabilityName, serviceType, address);
                    }
                };
                return new ContextServiceBuilder<T>(delegate, csi);
            }
        };
        ServiceTarget delegate = targetActiveStep.getScopedServiceTarget(modelController.getServiceTarget());
        ContextServiceTarget cst = new ContextServiceTarget(delegate, supplier, targetActiveStep.address);
        serviceTargets.add(cst);
        return cst;
    }


    Resource.ResourceEntry getActiveOperationResource() {
        return activeOperationResource;
    }

    private void takeWriteLock() {
        if (lockStep == null) {
            if (currentStage == Stage.DONE) {
                throw ControllerLogger.ROOT_LOGGER.invalidModificationAfterCompletedStep();
            }
            ExecutionStatus originalStatus = executionStatus;
            try {
                executionStatus = ExecutionStatus.AWAITING_OTHER_OPERATION;
                // BES 2014/04/22 Ignore blocking timeout here. We risk some bug causing the
                // lock to never be released. But we gain multiple ops being able to wait until they get
                // a chance to run with no need to guess how long op 2 will take so we can
                // let op 3 block for the time needed for both 1 and 2
//                int timeout = blockingTimeout.getBlockingTimeout();
//                if (timeout < 1) {
                modelController.acquireWriteLock(operationId, respectInterruption);
//                } else {
//                    // Wait longer than the standard amount to get a chance to execute
//                    // after whatever was holding the lock times out
//                    timeout += 10;
//                    if (!modelController.acquireLock(operationId, respectInterruption, timeout)) {
//                        throw MESSAGES.operationTimeoutAwaitingControllerLock(timeout);
//                    }
//                }
                exclusiveStartTime = System.nanoTime();
                lockStep = activeStep;
            } catch (InterruptedException e) {
                cancelled = true;
                Thread.currentThread().interrupt();
                throw ControllerLogger.ROOT_LOGGER.operationCancelledAsynchronously();
            } finally {
                executionStatus = originalStatus;
            }
        }
    }

    private void ensureWriteLockForRuntime() {
        if (!affectsRuntime) {
            takeWriteLock();
            affectsRuntime = true;
            if (containerMonitorStep == null) {
                if (currentStage == Stage.DONE) {
                    throw ControllerLogger.ROOT_LOGGER.invalidModificationAfterCompletedStep();
                }
                containerMonitorStep = activeStep;
                int timeout = getBlockingTimeout().getLocalBlockingTimeout();
                ExecutionStatus origStatus = executionStatus;
                try {
                    executionStatus = ExecutionStatus.AWAITING_STABILITY;
                    modelController.awaitContainerStability(timeout, TimeUnit.MILLISECONDS, respectInterruption);
                    notifyModificationBegun();
                } catch (InterruptedException e) {
                    if (resultAction != ResultAction.ROLLBACK) {
                        // We're not on the way out, so we've been cancelled on the way in
                        cancelled = true;
                    }
                    Thread.currentThread().interrupt();
                    throw ControllerLogger.ROOT_LOGGER.operationCancelledAsynchronously();
                } catch (TimeoutException te) {

                    getBlockingTimeout().timeoutDetected();
                    // This is the first step trying to await stability for this op, so if it's
                    // unstable some previous step must have messed it up and it can't recover.
                    // So this process must restart.
                    // The previous op should have set this in {@code releaseStepLocks}; doing it again
                    // here is just a 2nd line of defense
                    processState.setRestartRequired();// don't use our restartRequired() method as this is not reversible in rollback

                    // Deliberate log and throw; we want this logged, we need to notify user, and I want slightly
                    // different messages for both so just throwing a RuntimeException to get the automatic handling
                    // in AbstractOperationContext.executeStep is not what I wanted
                    ControllerLogger.MGMT_OP_LOGGER.timeoutAwaitingInitialStability(timeout / 1000, activeStep.operationId.name, activeStep.operationId.address);
                    setRollbackOnly();
                    throw new OperationFailedRuntimeException(ControllerLogger.ROOT_LOGGER.timeoutAwaitingInitialStability());
                } finally {
                    executionStatus = origStatus;
                }
            }
        } else if (!notifiedModificationBegun) {
            // We were asked to lock, but affectsRuntime was set while notifiedModificationBegun wasn't
            // This means we must be trying to modify again after we cleared the notifiedModificationBegun marker,
            // i.e. in a rollback
            notifyModificationBegun();
        }
    }

    private void notifyModificationBegun() {
        final PathAddress pa = modelController.getModelControllerResourceAddress(managementModel);
        // this will return null if a host has been started with --empty-host-controller, but not yet added.
        if (pa != null) {
            Notification notification = new Notification(RUNTIME_MODIFICATION_BEGUN,
                    pa,
                    MGMT_OP_LOGGER.runtimeModificationBegun());
            notificationSupport.emit(notification);
            notifiedModificationBegun = true;
        }
    }

    @Override
    public Resource readResource(final PathAddress requestAddress) {
        return readResource(requestAddress, true);
    }

    @Override
    public Resource readResource(final PathAddress requestAddress, final boolean recursive) {
        final PathAddress address = activeStep.address.append(requestAddress);
        return readResourceFromRoot(address, recursive);
    }

    @Override
    public Resource readResourceFromRoot(final PathAddress address) {
        return readResourceFromRoot(address, true);
    }

    @Override
    public Resource readResourceFromRoot(final PathAddress address, final boolean recursive) {
        assert isControllingThread();
        assertNotComplete(currentStage);
        //Clone the operation to preserve all the headers
        ModelNode operation = activeStep.operation.clone();
        operation.get(OP).set(ReadResourceHandler.DEFINITION.getName());
        operation.get(OP_ADDR).set(address.toModelNode());
        OperationId opId = new OperationId(operation);
        AuthorizationResult authResult = authorize(opId, operation, false, READ_CONFIG);
        if (authResult.getDecision() == AuthorizationResult.Decision.DENY) {
            // See if the problem was addressability
            AuthorizationResult addressResult = authorize(opId, operation, false, ADDRESS);
            if (addressResult.getDecision() == AuthorizationResult.Decision.DENY) {
                throw new ResourceNotAddressableException(activeStep.address);
            }
            throw ControllerLogger.ROOT_LOGGER.unauthorized(activeStep.operationId.name, activeStep.address, authResult.getExplanation());
        }
        return readResourceFromRoot(managementModel, address, recursive);
    }

    @Override
    Resource readResourceFromRoot(ManagementModel managementModel, PathAddress address, boolean recursive) {
        //
        // TODO double check authorization checks for this!
        //
        Resource model = managementModel.getRootResource();
        final Iterator<PathElement> iterator = address.iterator();
        while(iterator.hasNext()) {
            final PathElement element = iterator.next();
            // Allow wildcard navigation for the last element
            if(element.isWildcard() && ! iterator.hasNext()) {
                final Set<Resource.ResourceEntry> children = model.getChildren(element.getKey());
                if(children.isEmpty()) {
                    final PathAddress parent = address.subAddress(0, address.size() -1);
                    final Set<String> childrenTypes = managementModel.getRootResourceRegistration().getChildNames(parent);
                    if(! childrenTypes.contains(element.getKey())) {
                        throw ControllerLogger.ROOT_LOGGER.managementResourceNotFound(address);
                    }
                    // Return an empty model
                    return Resource.Factory.create();
                }
                model = Resource.Factory.create();
                for(final Resource.ResourceEntry entry : children) {
                    model.registerChild(entry.getPathElement(), entry);
                }
            } else {
                model = requireChild(model, element, address);
            }
        }
        if(recursive) {
            return model.clone();
        } else {
            return model.shallowCopy();
        }
    }

    @Override
    public Resource readResourceForUpdate(PathAddress requestAddress) {

        readOnly = false;

        assert isControllingThread();
        assertStageModel(currentStage);

        final PathAddress address = activeStep.address.append(requestAddress);

        // WFLY-3017 See if this write means a persistent config change
        // For speed, we assume all calls during boot relate to persistent config
        boolean runtimeOnly = !isBooting() && isResourceRuntimeOnly(address);

        if (!runtimeOnly) {
            rejectUserDomainServerUpdates();
        }
        checkHostServerGroupTracker(address);
        authorize(false, runtimeOnly ? READ_WRITE_RUNTIME : READ_WRITE_CONFIG);
        ensureLocalRootResource();
        affectsModel.put(address, NULL);
        Resource resource = this.managementModel.getRootResource();
        for (PathElement element : address) {
            if (element.isMultiTarget()) {
                throw ControllerLogger.ROOT_LOGGER.cannotWriteTo("*");
            }
            resource = requireChild(resource, element, address);
        }
        return resource;
    }

    private boolean isResourceRuntimeOnly(PathAddress fullAddress) {
        Resource resource = this.managementModel.getRootResource();
        for (Iterator<PathElement> it = fullAddress.iterator(); it.hasNext() && resource != null;) {
            PathElement element = it.next();
            if (element.isMultiTarget()) {
                resource = null;
            } else {
                resource = resource.getChild(element);
            }
        }

        if (resource != null) {
            return resource.isRuntime();
        }
        // No resource -- op will eventually fail
        ImmutableManagementResourceRegistration mrr = managementModel.getRootResourceRegistration().getSubModel(fullAddress);
        return mrr != null && mrr.isRuntimeOnly();
    }

    @Override
    public Resource getOriginalRootResource() {
        // TODO restrict
        return originalModel.getRootResource().clone();
    }

    @Override
    public Resource createResource(PathAddress relativeAddress) {
        return createResourceInternal(relativeAddress, -1);
    }

    private Resource createResourceInternal(PathAddress relativeAddress, int index) throws UnsupportedOperationException {
        ImmutableManagementResourceRegistration current = getResourceRegistration();
        ImmutableManagementResourceRegistration mrr = relativeAddress == PathAddress.EMPTY_ADDRESS ? current : current.getSubModel(relativeAddress);
        final Resource toAdd = Resource.Factory.create(mrr.isRuntimeOnly());
        addResourceInternal(relativeAddress, index, toAdd);
        return toAdd;
    }

    @Override
    public void addResource(PathAddress relativeAddress, Resource toAdd) {
        addResourceInternal(relativeAddress, -1, toAdd);
    }

    @Override
    public void addResource(PathAddress relativeAddress, int index, Resource toAdd) {
        assert index >= 0 : "index must be 0 or greater";
        addResourceInternal(relativeAddress, index, toAdd);
    }

    private void addResourceInternal(PathAddress relativeAddress, int index, Resource toAdd) {
        readOnly = false;

        assert isControllingThread();
        assertStageModel(currentStage);

        final PathAddress absoluteAddress = activeStep.address.append(relativeAddress);
        if (absoluteAddress.size() == 0) {
            throw ControllerLogger.ROOT_LOGGER.duplicateResourceAddress(absoluteAddress);
        }

        boolean runtimeOnly = toAdd.isRuntime();

        if (!runtimeOnly) {
            // Check for user updates to a domain server model
            rejectUserDomainServerUpdates();
        }
        checkHostServerGroupTracker(absoluteAddress);
        authorizeAdd(runtimeOnly);
        ensureLocalRootResource();
        affectsModel.put(absoluteAddress, NULL);
        Resource model = this.managementModel.getRootResource();
        final Iterator<PathElement> i = absoluteAddress.iterator();
        while (i.hasNext()) {
            final PathElement element = i.next();
            if (element.isMultiTarget()) {
                throw ControllerLogger.ROOT_LOGGER.cannotWriteTo("*");
            }
            if (! i.hasNext()) {
                final String key = element.getKey();
                if(model.hasChild(element)) {
                    throw ControllerLogger.ROOT_LOGGER.duplicateResourceAddress(absoluteAddress);
                } else {
                    final PathAddress parent = absoluteAddress.subAddress(0, absoluteAddress.size() -1);
                    final Set<String> childrenNames = managementModel.getRootResourceRegistration().getChildNames(parent);
                    if(!childrenNames.contains(key)) {
                        throw ControllerLogger.ROOT_LOGGER.noChildType(key);
                    }
                    if (index < 0) {
                        model.registerChild(element, toAdd);
                    } else {
                        model.registerChild(element, index, toAdd);
                    }
                    model = toAdd;
                }
            } else {
                model = model.getChild(element);
                if (model == null) {
                    PathAddress ancestor = PathAddress.EMPTY_ADDRESS;
                    for (PathElement pe : absoluteAddress) {
                        ancestor = ancestor.append(pe);
                        if (element.equals(pe)) {
                            break;
                        }
                    }
                    throw ControllerLogger.ROOT_LOGGER.resourceNotFound(ancestor, absoluteAddress);
                }
            }
        }

        Notification notification = new Notification(RESOURCE_ADDED_NOTIFICATION, absoluteAddress, ControllerLogger.ROOT_LOGGER.resourceWasAdded(absoluteAddress));
        emit(notification);
    }

    @Override
    public Resource removeResource(final PathAddress requestAddress) {

        readOnly = false;

        assert isControllingThread();
        assertStageModel(currentStage);

        final PathAddress address = activeStep.address.append(requestAddress);

        // WFLY-3017 See if this write means a persistent config change
        // For speed, we assume all calls during boot relate to persistent config
        boolean runtimeOnly = isResourceRuntimeOnly(address);
        if (!runtimeOnly) {
            rejectUserDomainServerUpdates();
        }
        checkHostServerGroupTracker(address);
        authorize(false, runtimeOnly ? READ_WRITE_RUNTIME : READ_WRITE_CONFIG);
        ensureLocalRootResource();
        affectsModel.put(address, NULL);
        Resource model = this.managementModel.getRootResource();
        final Iterator<PathElement> i = address.iterator();
        while (i.hasNext()) {
            final PathElement element = i.next();
            if (element.isMultiTarget()) {
                throw ControllerLogger.ROOT_LOGGER.cannotRemove("*");
            }
            if (!i.hasNext()) {
                model = model.removeChild(element);
            } else {
                model = requireChild(model, element, address);
            }
        }

        Notification notification = new Notification(RESOURCE_REMOVED_NOTIFICATION, address, ControllerLogger.ROOT_LOGGER.resourceWasRemoved(address));
        emit(notification);

        return model;
    }

    @Override
    public void acquireControllerLock() {
        takeWriteLock();
    }

    @Override
    public boolean isModelAffected() {
        return affectsResourceTree;
    }

    @Override
    public boolean isRuntimeAffected() {
        return affectsRuntime;
    }

    @Override
    public boolean isResourceRegistryAffected() {
        return affectsResourceRegistration;
    }

    @Override
    public Stage getCurrentStage() {
        return currentStage;
    }

    @Override
    public void report(final MessageSeverity severity, final String message) {
        try {
            if(messageHandler != null) {
                messageHandler.handleReport(severity, message);
            }
        } catch (Throwable t) {
            // ignored
        }
    }

    @Override
    void handleUncaughtException(RuntimeException e) {
        try {
            if (lockStep != null) {
                releaseModelControllerLock();
            }
        } finally {
            if (containerMonitorStep != null) {
                resetContainerStateChanges();
            }
        }
    }

    @Override
    void releaseStepLocks(AbstractOperationContext.Step step) {
        boolean interrupted = false;
        try {
            // Get container stability before releasing controller lock to ensure another
            // op doesn't get in and destabilize the container.
            if (this.containerMonitorStep == step) {
                // Note: If we allow this thread to be interrupted, an op that has been cancelled
                // because of minor user impatience can release the controller lock while the
                // container is unsettled. OTOH, if we don't allow interruption, if the
                // container can't settle (e.g. a broken service is blocking in start()), the operation
                // will not be cancellable. I (BES 2012/01/24) chose the former as the lesser evil.
                // Any subsequent step that calls getServiceRegistry/getServiceTarget/removeService
                // is going to have to await the monitor uninterruptibly anyway before proceeding.
                long timeout = getBlockingTimeout().getLocalBlockingTimeout();
                try {
                    modelController.awaitContainerStability(timeout, TimeUnit.MILLISECONDS, true);
                }  catch (InterruptedException e) {
                    // Cancelled in some way
                    interrupted = true;
                    // Cancellation prevented us ensuring that MSC will be stable when this operation completes.
                    // We can no longer have any sense of MSC state or how the model relates to the runtime and
                    // we need to start from a fresh service container. Notify the controller of this.
                    modelController.containerCannotStabilize();
                    MGMT_OP_LOGGER.interruptedWaitingStability(activeStep.operationId.name, activeStep.operationId.address);
                } catch (TimeoutException te) {
                    // If we can't attain stability on the way out after rollback ops have run,
                    // we can no longer have any sense of MSC state or how the model relates to the runtime and
                    // we need to start from a fresh service container. Notify the controller of this.
                    modelController.containerCannotStabilize();
                    // Just log; this doesn't change the result of the op. And if we're not stable here
                    // it's almost certain we never stabilized during execution or we are rolling back and destabilized there.
                    // Either one means there is already a failure message associated with this op.
                    MGMT_OP_LOGGER.timeoutCompletingOperation(timeout / 1000, activeStep.operationId.name, activeStep.operationId.address);
                }
            }

            if (this.lockStep == step) {
                releaseModelControllerLock();
            }
        } finally {
            try {
                if (this.containerMonitorStep == step) {
                    notifyModificationsComplete();
                    resetContainerStateChanges();
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void releaseModelControllerLock() {
        modelController.releaseWriteLock(operationId);
        exclusiveStartTime = -1;
        lockStep = null;
    }

    private void resetContainerStateChanges() {
        modelController.logContainerStateChangesAndReset();
        containerMonitorStep = null;
    }

    @Override
    boolean isReadOnly() {
        return readOnly;
    }

    @Override
    ManagementResourceRegistration getRootResourceRegistrationForUpdate() {
        return getMutableResourceRegistration(null);
    }

    private static Resource requireChild(final Resource resource, final PathElement childPath, final PathAddress fullAddress) {
        if (resource.hasChild(childPath)) {
            return resource.requireChild(childPath);
        } else {
            PathAddress missing = PathAddress.EMPTY_ADDRESS;
            for (PathElement search : fullAddress) {
                missing = missing.append(search);
                if (search.equals(childPath)) {
                    break;
                }
            }
            throw ControllerLogger.ROOT_LOGGER.managementResourceNotFound(missing);
        }
    }

    @Override
    public ModelNode resolveExpressions(ModelNode node) throws OperationFailedException {
        return modelController.resolveExpressions(node);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> V getAttachment(final AttachmentKey<V> key) {
        return contextAttachments.getAttachment(key);
    }

    @Override
    void logAuditRecord() {
        super.logAuditRecord();
    }

    @Override
    public <V> V attach(final AttachmentKey<V> key, final V value) {
        return  contextAttachments.attach(key, value);
    }

    @Override
    public <V> V attachIfAbsent(final AttachmentKey<V> key, final V value) {
        return contextAttachments.attachIfAbsent(key, value);
    }

    @Override
    public <V> V detach(final AttachmentKey<V> key) {
        return contextAttachments.detach(key);
    }

    @Override
    public AuthorizationResult authorize(ModelNode operation) {
        return authorize(operation, EnumSet.noneOf(Action.ActionEffect.class));
    }

    @Override
    public AuthorizationResult authorize(ModelNode operation, Set<Action.ActionEffect> effects) {
        OperationId opId = new OperationId(operation);
        return authorize(opId, operation, false, effects);
    }

    @Override
    public AuthorizationResult authorize(ModelNode operation, String attribute, ModelNode currentValue) {
        return authorize(operation, attribute, currentValue, EnumSet.noneOf(Action.ActionEffect.class));
    }

    @Override
    public AuthorizationResult authorize(ModelNode operation, String attribute, ModelNode currentValue, Set<Action.ActionEffect> effects) {
        OperationId opId = new OperationId(operation);
        AuthorizationResult resourceResult = authorize(opId, operation, false, effects);
        if (resourceResult.getDecision() == AuthorizationResult.Decision.DENY) {
            return resourceResult;
        }
        return authorize(opId, attribute, currentValue, effects);
    }

    @Override
    public AuthorizationResponseImpl authorizeResource(boolean attributes, boolean isDefaultResponse) {
        ModelNode op = new ModelNode();
        op.get(OP).set(isDefaultResponse ? GlobalOperationHandlers.CHECK_DEFAULT_RESOURCE_ACCESS : GlobalOperationHandlers.CHECK_RESOURCE_ACCESS);
        op.get(OP_ADDR).set(activeStep.operation.get(OP_ADDR));
        if (activeStep.operation.hasDefined(OPERATION_HEADERS)) {
            op.get(OPERATION_HEADERS).set(activeStep.operation.get(OPERATION_HEADERS));
        }
        OperationId opId = new OperationId(op);
        AuthorizationResponseImpl authResp = authorizations.get(opId);
        if (authResp == null) {
            authResp = getBasicAuthorizationResponse(opId, op);
        }
        if (authResp == null) {
            // Non-existent resource type or operation. This is permitted but will fail
            // later for reasons unrelated to authz
            return null;
        }
        Environment callEnvironment = getCallEnvironment();
        if (authResp.getResourceResult(ActionEffect.ADDRESS) == null) {
            Action action = authResp.standardAction.limitAction(ActionEffect.ADDRESS);
            authResp.addResourceResult(ActionEffect.ADDRESS, modelController.getAuthorizer().authorize(getCaller(), callEnvironment, action, authResp.targetResource));
        }

        if (authResp.getResourceResult(ActionEffect.ADDRESS).getDecision() == Decision.PERMIT) {
            for (Action.ActionEffect requiredEffect : ALL_READ_WRITE) {
                AuthorizationResult effectResult = authResp.getResourceResult(requiredEffect);
                if (effectResult == null) {
                    Action action = authResp.standardAction.limitAction(requiredEffect);
                    effectResult = modelController.getAuthorizer().authorize(getCaller(), callEnvironment, action, authResp.targetResource);
                    authResp.addResourceResult(requiredEffect, effectResult);
                }
            }
        }
        if (attributes) {
            ImmutableManagementResourceRegistration mrr = authResp.targetResource.getResourceRegistration();
            if (!authResp.attributesComplete) {
                for (String attr : mrr.getAttributeNames(PathAddress.EMPTY_ADDRESS)) {
                    TargetAttribute targetAttribute = null;
                    if (authResp.getAttributeResult(attr, ActionEffect.ADDRESS) == null) {
//                        if (targetAttribute == null) {
//                            targetAttribute = createTargetAttribute(authResp, attr, isDefaultResponse);
//                        }
//                        Action action = authResp.standardAction.limitAction(ActionEffect.ADDRESS);
//                        authResp.addAttributeResult(attr, ActionEffect.ADDRESS, modelController.getAuthorizer().authorize(getCaller(), callEnvironment, action, targetAttribute));
                        // ADDRESS does not apply to attributes
                        authResp.addAttributeResult(attr, ActionEffect.ADDRESS, AuthorizationResult.PERMITTED);
                    }
                    for (Action.ActionEffect actionEffect : ALL_READ_WRITE) {
                        AuthorizationResult authResult = authResp.getAttributeResult(attr, actionEffect);
                        if (authResult == null) {
                            Action action = authResp.standardAction.limitAction(actionEffect);
                            if (targetAttribute == null) {
                                targetAttribute = createTargetAttribute(authResp, attr, isDefaultResponse);
                            }
                            authResult = modelController.getAuthorizer().authorize(getCaller(), callEnvironment, action, targetAttribute);
                            authResp.addAttributeResult(attr, actionEffect, authResult);
                        }
                    }
                }
                authResp.attributesComplete = true;
            }
        }

        return authResp;
    }

    @Override
    Resource getModel() {
        return managementModel.getRootResource();
    }

    @Override
    ResultAction executeOperation() {
        try {
            if (!isBooting()) {
                attach(BlockingTimeout.Factory.ATTACHMENT_KEY, getBlockingTimeout());
            }
            return super.executeOperation();
        } finally {
            try {
                // Decouple any ServiceTarget or ServiceRegistry we have created from this object
                try {
                    for (ContextServiceTarget serviceTarget : serviceTargets) {
                        serviceTarget.done();
                    }
                    serviceTargets.clear();
                } finally {
                    for (OperationContextServiceRegistry serviceRegistry : serviceRegistries) {
                        serviceRegistry.done();
                    }
                    serviceRegistries.clear();
                }
            } finally {
                synchronized (done) {
                    if (done.done) {
                        // late cancellation; clear the thread status
                        Thread.interrupted();
                    } else {
                        done.done = true;
                    }
                }
            }
        }
    }

    private TargetAttribute createTargetAttribute(AuthorizationResponseImpl authResp, String attributeName, boolean isDefaultResponse) {
        ModelNode model = authResp.targetResource.getResource().getModel();
        ModelNode currentValue;
        if (isDefaultResponse) {
            //Just use an empty model node to avoid using vault expressions
            currentValue = new ModelNode();
        } else {
            currentValue = model.has(attributeName) ? model.get(attributeName) : new ModelNode();
        }
        AttributeAccess attributeAccess = authResp.targetResource.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
        return new TargetAttribute(attributeName, attributeAccess, currentValue, authResp.targetResource);

    }

    @Override
    public AuthorizationResult authorizeOperation(ModelNode operation) {
        OperationId opId = new OperationId(operation);
        AuthorizationResult resourceResult = authorize(opId, operation, false, EnumSet.of(ActionEffect.ADDRESS));
        if (resourceResult.getDecision() == AuthorizationResult.Decision.DENY) {
            return resourceResult;
        }

        if (isBooting()) {
            return AuthorizationResult.PERMITTED;
        } else {
            final String operationName = operation.require(OP).asString();
            AuthorizationResponseImpl authResp = authorizations.get(opId);
            assert authResp != null : "perform resource authorization before operation authorization";

            AuthorizationResult authResult = authResp.getOperationResult(operationName);
            if (authResult == null) {
                OperationEntry operationEntry = authResp.targetResource.getResourceRegistration().getOperationEntry(PathAddress.EMPTY_ADDRESS, operationName);

                operation.get(OPERATION_HEADERS).set(activeStep.operation.get(OPERATION_HEADERS));
                Action targetAction = new Action(operation, operationEntry);

                authResult = modelController.getAuthorizer().authorize(getCaller(), getCallEnvironment(), targetAction, authResp.targetResource);
                authResp.addOperationResult(operationName, authResult);

                //When authorizing the 'add' operation, make sure that all the attributes are accessible
                if (authResult.getDecision() == AuthorizationResult.Decision.PERMIT && operationName.equals(ModelDescriptionConstants.ADD)) {
                    if (!authResp.attributesComplete) {
                        authResp = authorizeResource(true, false);
                        authResp.addOperationResult(operationName, authResult);
                    }
                    authResult = authResp.validateAddAttributeEffects(ADD, targetAction.getActionEffects(), activeStep.operation);
                }
            }

            if (authResult.getDecision() == AuthorizationResult.Decision.DENY) {
                return authResult;
            }

            return AuthorizationResult.PERMITTED;
        }
    }

    @Override
    public void registerCapability(RuntimeCapability capability) {
        registerCapability(capability, activeStep, null);
    }

    void registerCapability(RuntimeCapability capability, Step step, String attribute) {
        assert isControllingThread();
        assertStageModel(currentStage);
        ensureLocalCapabilityRegistry();
        RuntimeCapabilityRegistration registration = createCapabilityRegistration(capability, step, attribute);
        managementModel.getCapabilityRegistry().registerCapability(registration);
        for (String required : capability.getRequirements()) {
            RuntimeRequirementRegistration requirementRegistration = createRequirementRegistration(required, capability.getName(), false, step, attribute);
            recordRequirement(requirementRegistration, step);
        }
    }

    @Override
    public void registerAdditionalCapabilityRequirement(String required, String dependent, String attribute) {
        registerAdditionalCapabilityRequirement(required, dependent, activeStep, attribute);
    }

    void registerAdditionalCapabilityRequirement(String required, String dependent, Step step, String attribute) {
        assert isControllingThread();
        assertStageModel(currentStage);
        ensureLocalCapabilityRegistry();
        RuntimeRequirementRegistration requirementRegistration = createRequirementRegistration(required, dependent, false, step, attribute);
        managementModel.getCapabilityRegistry().registerAdditionalCapabilityRequirement(requirementRegistration);
        recordRequirement(requirementRegistration, step);
    }

    private void recordRequirement(RuntimeRequirementRegistration reg, Step step) {
        Set<Step> dependents = addedRequirements.get(reg);
        if (dependents == null) {
            dependents = new HashSet<>();
            Set<Step> existing = addedRequirements.putIfAbsent(reg, dependents);
            if (existing != null) {
                dependents = existing;
            }
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (dependents) {
            dependents.add(step);
        }
    }

    @Override
    public boolean hasOptionalCapability(String required, String dependent, String attribute) {
        return requestOptionalCapability(required, dependent, true, activeStep, attribute);
    }

    boolean requestOptionalCapability(String required, String dependent, boolean runtimeOnly, Step step, String attribute) {
        assert isControllingThread();
        assertCapabilitiesAvailable(currentStage);
        ensureLocalCapabilityRegistry();
        RuntimeCapabilityRegistry registry = managementModel.getCapabilityRegistry();
        if (dependent == null) {
            // WFCORE-900 we're currently forgiving of this, but only for runtime-only requirements
            assert runtimeOnly;
            CapabilityScope context = createCapabilityContext(step.address);
            return registry.hasCapability(required, context);
        }
        RuntimeRequirementRegistration registration = createRequirementRegistration(required, dependent, runtimeOnly, step, attribute);
        CapabilityScope context = registration.getDependentContext();
        if (registry.hasCapability(required, context)) {
            registry.registerAdditionalCapabilityRequirement(registration);
            recordRequirement(registration, step);
            return true;
        }
        return false;
    }

    @Override
    public void requireOptionalCapability(String required, String dependent, String attribute) throws OperationFailedException {
        requireOptionalCapability(required, dependent, activeStep, attribute);
    }

    void requireOptionalCapability(String required, String dependent, Step step, String attribute) throws OperationFailedException {
        if (!requestOptionalCapability(required, dependent, false, step, attribute)) {
            String msg = ControllerLogger.ROOT_LOGGER.requiredCapabilityMissing();
            if (getProcessType().isServer()) {
                msg += "\n" + ControllerLogger.ROOT_LOGGER.formattedCapabilityName(required);
            } else {
                msg = "\n" + ControllerLogger.ROOT_LOGGER.formattedCapabilityId(required, createCapabilityContext(step.address).getName());
            }
            throw new OperationFailedException(msg);
        }
    }

    @Override
    public void deregisterCapabilityRequirement(String required, String dependent) {
        deregisterCapabilityRequirement(required, dependent, null);
    }

    @Override
    public void deregisterCapabilityRequirement(String required, String dependent, String attribute) {
        removeCapabilityRequirement(required, dependent, activeStep, attribute);
    }

    void removeCapabilityRequirement(String required, String dependent, Step step, String attributeName) {
        assert isControllingThread();
        assertStageModel(currentStage);
        ensureLocalCapabilityRegistry();
        RuntimeRequirementRegistration registration = createRequirementRegistration(required, dependent, false, step, attributeName);
        managementModel.getCapabilityRegistry().removeCapabilityRequirement(registration);
        removeRequirement(required, registration.getDependentContext(), step);
    }

    @Override
    public void deregisterCapability(String capability) {
        removeCapability(capability, activeStep);
    }

    void removeCapability(String capabilityName, Step step) {
        assert isControllingThread();
        assertStageModel(currentStage);
        ensureLocalCapabilityRegistry();
        CapabilityScope context = createCapabilityContext(step.address);
        RuntimeCapabilityRegistration capReg = managementModel.getCapabilityRegistry().removeCapability(capabilityName, context, step.address);
        if (capReg != null) {
            RuntimeCapability capability = capReg.getCapability();
            for (String required : capability.getRequirements()) {
                removeRequirement(required, context, step);
            }
            removedCapabilities.put(capReg.getCapabilityId(), step);
        }
    }

    private void removeRequirement(String required, CapabilityScope context, Step step) {
        CapabilityId id = new CapabilityId(required, context);
        Set<Step> dependents = addedRequirements.get(id);
        if (dependents != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (dependents) {
                dependents.remove(step);
            }
        }
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) {
        return getCapabilityRuntimeAPI(capabilityName, apiType, activeStep);
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) {
        return getCapabilityRuntimeAPI(RuntimeCapability.buildDynamicCapabilityName(capabilityBaseName, dynamicPart), apiType, activeStep);
    }

    <T> T getCapabilityRuntimeAPI(String capabilityName, Class<T> apiType, Step step) {
        assert isControllingThread();
        assertCapabilitiesAvailable(currentStage);
        CapabilityScope context = createCapabilityContext(step.address);
        return managementModel.getCapabilityRegistry().getCapabilityRuntimeAPI(capabilityName, context, apiType);
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType) {
        return getCapabilityServiceName(capabilityName, serviceType, activeStep.address);
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityBaseName, String dynamicPart, Class<?> serviceType) {
        return getCapabilityServiceName(RuntimeCapability.buildDynamicCapabilityName(capabilityBaseName, dynamicPart), serviceType, activeStep.address);
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityBaseName, Class<?> serviceType, String ... dynamicParts) {
        return getCapabilityServiceName(RuntimeCapability.buildDynamicCapabilityName(capabilityBaseName, dynamicParts), serviceType, activeStep.address);
    }

    ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType, final PathAddress address) {
        assert isControllingThread();
        assertCapabilitiesAvailable(currentStage);
        CapabilityScope context = createCapabilityContext(address);
        try {
            return managementModel.getCapabilityRegistry().getCapabilityServiceName(capabilityName, context, serviceType);
        } catch (IllegalStateException ignored) {
            // not registered. just do it directly
        }
        ControllerLogger.ROOT_LOGGER.debugf("OperationContext: Parsing ServiceName for %s", capabilityName);
        return ServiceNameFactory.parseServiceName(capabilityName);
    }

    private void rejectUserDomainServerUpdates() {
        if (isModelUpdateRejectionRequired()) {
            ModelNode op = activeStep.operation;
            if (op.hasDefined(OPERATION_HEADERS, CALLER_TYPE) && USER.equals(op.get(OPERATION_HEADERS, CALLER_TYPE).asString())) {
                throw ControllerLogger.ROOT_LOGGER.modelUpdateNotAuthorized(op.require(OP).asString(), activeStep.address);
            }
        }
    }

    @Override
    public CapabilityServiceSupport getCapabilityServiceSupport() {
        assert isControllingThread();
        assertCapabilitiesAvailable(currentStage);
        return new CapabilityServiceSupportImpl(managementModel);
    }

    @Override
    CapabilityRegistry.RuntimeStatus getStepCapabilityStatus(Step step){

        RuntimeCapabilityRegistry.RuntimeStatus result = RuntimeCapabilityRegistry.RuntimeStatus.NORMAL;
        Map<CapabilityId, RuntimeCapabilityRegistry.RuntimeStatus> statuses =
                managementModel.getCapabilityRegistry().getRuntimeStatus(step.address, step.getManagementResourceRegistration(managementModel));
        if (statuses.isEmpty()) {
            // TODO. If there are no capabilities but the process is reload/restart required, then what?
            // Should we interpret this as meaning the step depends on all capabilities and something must be
            // reload/restart required, so prevent execution? Example: a deployment may not have a capability
            // but depends on the overall functioning of the system. User make some changes to some resources
            // (e.g. a datasource) that aren't reflected in the runtime, and then deploys a new version
            // of the app that assumes those resources are in the correct runtime state. But they won't
            // be until reload, so the deployment change should not take effect.
            // OTOH, doing this would prevent something like
            // /core-service=management/service=configuration-changes:write-attribute(name=max-history,value=5)
            // taking immediate effect. Which is silly. Unless we add a capability for it, which seems a bit overboard.
        } else {
            for (RuntimeCapabilityRegistry.RuntimeStatus status : statuses.values()) {
                if (status != RuntimeCapabilityRegistry.RuntimeStatus.NORMAL) {
                    result = status;
                    break;
                }
            }
        }
        return result;
    }


    private boolean isModelUpdateRejectionRequired() {
        if (requiresModelUpdateAuthorization == null) {
            requiresModelUpdateAuthorization = !isBooting() && getProcessType() == ProcessType.DOMAIN_SERVER;
        }
        return requiresModelUpdateAuthorization.booleanValue();
    }

    private void authorize(boolean allAttributes, Set<Action.ActionEffect> actionEffects) {
        AuthorizationResult accessResult = authorize(activeStep.operationId, activeStep.operation, false, ADDRESS);
        if (accessResult.getDecision() == AuthorizationResult.Decision.DENY) {
            if (activeStep.address.size() > 0) {
                throw new ResourceNotAddressableException(activeStep.address);
            } else {
                // WFLY-2037 -- the root resource isn't hidden; if we hit this it means the user isn't authorized
                throw ControllerLogger.ROOT_LOGGER.unauthorized(activeStep.operationId.name, activeStep.address, accessResult.getExplanation());
            }
        }
        AuthorizationResult authResult = authorize(activeStep.operationId, activeStep.operation, allAttributes, actionEffects);
        if (authResult.getDecision() == AuthorizationResult.Decision.DENY) {
            throw ControllerLogger.ROOT_LOGGER.unauthorized(activeStep.operationId.name, activeStep.address, authResult.getExplanation());
        }
    }

    private void authorizeAdd(boolean runtimeOnly) {
        AuthorizationResult accessResult = authorize(activeStep.operationId, activeStep.operation, false, ADDRESS);
        if (accessResult.getDecision() == AuthorizationResult.Decision.DENY) {
            throw new ResourceNotAddressableException(activeStep.address);
        }
        final Set<Action.ActionEffect> writeEffect = runtimeOnly ? WRITE_RUNTIME : WRITE_CONFIG;
        AuthorizationResult authResult = authorize(activeStep.operationId, activeStep.operation, true, writeEffect);
        if (authResult.getDecision() == AuthorizationResult.Decision.DENY) {
            AuthorizationResponseImpl authResp = authorizations.get(activeStep.operationId);
            assert authResp != null : "no AuthorizationResponse";
            String opName = activeStep.operation.get(OP).asString();
            authResp.addOperationResult(opName, authResult);
            authResult = authResp.validateAddAttributeEffects(opName, writeEffect, activeStep.operation);
            authResp.addOperationResult(opName, authResult);
            if (authResult.getDecision() == AuthorizationResult.Decision.DENY) {
                throw ControllerLogger.ROOT_LOGGER.unauthorized(activeStep.operationId.name, activeStep.address, authResult.getExplanation());
            }
        }
    }

    private AuthorizationResult authorize(OperationId opId, ModelNode operation, boolean allAttributes, Set<Action.ActionEffect> actionEffects) {
        if (isBooting()) {
            return AuthorizationResult.PERMITTED;
        } else {
            AuthorizationResponseImpl authResp = authorizations.get(opId);
            if (authResp == null) {
                authResp = getBasicAuthorizationResponse(opId, operation);
            }
            if (authResp == null) {
                // Non-existent resource type or operation. This is permitted but will fail
                // later for reasons unrelated to authz
                return AuthorizationResult.PERMITTED;
            }
            for (Action.ActionEffect requiredEffect : actionEffects) {
                AuthorizationResult effectResult = authResp.getResourceResult(requiredEffect);
                if (effectResult == null) {
                    Action action = authResp.standardAction.limitAction(requiredEffect);
                    effectResult = modelController.getAuthorizer().authorize(getCaller(), getCallEnvironment(), action, authResp.targetResource);
                    authResp.addResourceResult(requiredEffect, effectResult);
                }
                if (effectResult.getDecision() == AuthorizationResult.Decision.DENY) {
                    return effectResult;
                }
            }
            AuthorizationResult errResult = null;

            if (allAttributes) {
                ImmutableManagementResourceRegistration mrr = authResp.targetResource.getResourceRegistration();
                ModelNode model = authResp.targetResource.getResource().getModel();
                Set<Action.ActionEffect> attributeEffects = actionEffects.isEmpty() ? authResp.standardAction.getActionEffects() : actionEffects;

                for (String attr : mrr.getAttributeNames(PathAddress.EMPTY_ADDRESS)) {
                    ModelNode currentValue = model.has(attr) ? model.get(attr) : new ModelNode();
                    AuthorizationResult attrResult = authorize(opId, attr, currentValue, attributeEffects);
                    if (attrResult.getDecision() == AuthorizationResult.Decision.DENY) {

                        errResult = attrResult;
                    }
                }
                authResp.attributesComplete = true;
            }

            return errResult != null ? errResult : AuthorizationResult.PERMITTED;
        }
    }

    private AuthorizationResult authorize(OperationId operationId, String attribute,
                                          ModelNode currentValue, Set<Action.ActionEffect> actionEffects) {
        if (isBooting()) {
            return AuthorizationResult.PERMITTED;
        } else {
            AuthorizationResponseImpl authResp = authorizations.get(operationId);
            assert authResp != null : "perform resource authorization before attribute authorization";

            TargetAttribute targetAttribute = null;
            Set<Action.ActionEffect> attributeEffects = actionEffects.isEmpty() ? authResp.standardAction.getActionEffects() : actionEffects;
            for (Action.ActionEffect actionEffect : attributeEffects) {
                AuthorizationResult authResult = authResp.getAttributeResult(attribute, actionEffect);
                if (authResult == null) {
                    Action action = authResp.standardAction.limitAction(actionEffect);
                    if (targetAttribute == null) {
                        AttributeAccess attributeAccess = authResp.targetResource.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attribute);
                        targetAttribute = new TargetAttribute(attribute, attributeAccess, currentValue, authResp.targetResource);
                    }
                    authResult = modelController.getAuthorizer().authorize(getCaller(), getCallEnvironment(), action, targetAttribute);
                    authResp.addAttributeResult(attribute, actionEffect, authResult);
                }
                if (authResult.getDecision() == AuthorizationResult.Decision.DENY) {
                    return authResult;
                }
            }

            return AuthorizationResult.PERMITTED;
        }
    }

    private AuthorizationResponseImpl getBasicAuthorizationResponse(OperationId opId, ModelNode operation) {
        Caller caller = getCaller();
        ImmutableManagementResourceRegistration mrr = managementModel.getRootResourceRegistration().getSubModel(opId.address);
        if (mrr == null) {
            return null;
        }
        Action action = getAuthorizationAction(mrr, opId.name, operation);
        if (action == null) {
            return null;
        }

        Resource resource = getAuthorizationResource(opId.address);
        ProcessType processType = getProcessType();
        TargetResource targetResource;
        if (processType.isManagedDomain()) {
            HostServerGroupTracker.HostServerGroupEffect hostServerGroupEffect;
            if (processType.isServer()) {
                ModelNode rootModel = managementModel.getRootResource().getModel();
                String serverGroup = rootModel.get(SERVER_GROUP).asString();
                String host = rootModel.get(HOST).asString();
                hostServerGroupEffect = HostServerGroupTracker.HostServerGroupEffect.forServer(opId.address, serverGroup, host);
            } else {
                hostServerGroupEffect =
                                    hostServerGroupTracker.getHostServerGroupEffects(opId.address, operation, managementModel.getRootResource());
            }
            targetResource = TargetResource.forDomain(opId.address, mrr, resource, hostServerGroupEffect, hostServerGroupEffect);
        } else {
            targetResource = TargetResource.forStandalone(opId.address, mrr, resource);
        }


        AuthorizationResponseImpl result = new AuthorizationResponseImpl(action, targetResource);
        AuthorizationResult simple = modelController.getAuthorizer().authorize(caller, getCallEnvironment(), action, targetResource);
        if (simple.getDecision() == AuthorizationResult.Decision.PERMIT) {
            for (Action.ActionEffect actionEffect : action.getActionEffects()) {
                result.addResourceResult(actionEffect, simple);
            }
        }
        // else something was denied. Find out exactly what was denied when needed
        authorizations.put(opId, result);
        return result;
    }

    private Resource getAuthorizationResource(PathAddress address) {
        Resource model = this.managementModel.getRootResource();
        for (PathElement element : address) {
            // Allow wildcard navigation for the last element
            if (element.isWildcard()) {
                model = Resource.Factory.create();
                final Set<Resource.ResourceEntry> children = model.getChildren(element.getKey());
                for (final Resource.ResourceEntry entry : children) {
                    model.registerChild(entry.getPathElement(), entry);
                }
            } else {
                model = model.getChild(element);
                if (model == null) {
                    return Resource.Factory.create();
                }
            }
        }
        return model;
    }

    private Action getAuthorizationAction(ImmutableManagementResourceRegistration mrr, String operationName, ModelNode operation) {
        OperationEntry entry = mrr.getOperationEntry(PathAddress.EMPTY_ADDRESS, operationName);
        if (entry == null) {
            return null;
        }
        return new Action(operation, entry);
    }

    private void checkHostServerGroupTracker(PathAddress pathAddress) {
        if (hostServerGroupTracker != null && !isBooting()) {
            if (pathAddress.size() > 0) {
                String key0 = pathAddress.getElement(0).getKey();
                if (SERVER_GROUP.equals(key0)
                        || (pathAddress.size() > 1 && HOST.equals(key0))
                                && SERVER_CONFIG.equals(pathAddress.getElement(1).getKey())) {
                    hostServerGroupTracker = new HostServerGroupTracker();
                }
            }
        }
    }

    private BlockingTimeout getBlockingTimeout() {
        if (blockingTimeout == null) {
            synchronized (this) {
                if (blockingTimeout == null) {
                    blockingTimeout = new BlockingTimeoutImpl(operationHeaders.getBlockingTimeout());
                }
            }
        }
        return blockingTimeout;
    }

    private synchronized void ensureLocalRootResource() {
        if (!affectsResourceTree) {
            takeWriteLock();
            managementModel = managementModel.cloneRootResource();
            affectsResourceTree = true;
        }
    }

    private synchronized void ensureLocalManagementResourceRegistration() {
        if (!affectsResourceRegistration) {
            takeWriteLock();
            // TODO call this if we decide to make the MRR cloneable
            //managementModel = managementModel.cloneRootResourceRegistration();
            affectsResourceRegistration = true;
        }
    }

    private synchronized void ensureLocalCapabilityRegistry() {
        if (!affectsCapabilityRegistry) {
            takeWriteLock();
            affectsCapabilityRegistry = true;
        }
    }

    private boolean isRuntimeChangeAllowed() {
        assertNotComplete(currentStage);
        boolean validStage = currentStage == Stage.RUNTIME || currentStage == Stage.VERIFY || isRollingBack();
        return validStage && (getProcessType().isServer() || activeStep == null || activeStep.address.size() < 2 || !PROFILE.equals(activeStep.address.getElement(0).getKey()));
    }

    private static void assertNotComplete(final Stage currentStage) {
        if (currentStage == null) {
            throw ControllerLogger.ROOT_LOGGER.operationAlreadyComplete();
        }
    }

    private static void assertStageModel(final Stage currentStage) {
        assertNotComplete(currentStage);
        if (currentStage != Stage.MODEL) {
            throw ControllerLogger.ROOT_LOGGER.stageAlreadyComplete(Stage.MODEL);
        }
    }

    private static void assertCapabilitiesAvailable(final Stage currentStage) {
        assertNotComplete(currentStage);
        if (currentStage == Stage.MODEL) {
            throw ControllerLogger.ROOT_LOGGER.capabilitiesNotAvailable(currentStage, Stage.RUNTIME);
        }
    }

    private RuntimeRequirementRegistration createRequirementRegistration(String required, String dependent,
                                                                         boolean runtimeOnly, Step step, String attribute) {
        CapabilityScope context = createCapabilityContext(step.address);
        RegistrationPoint rp = new RegistrationPoint(step.address, attribute);
        return new RuntimeRequirementRegistration(required, dependent, context, rp, runtimeOnly);
    }

    private RuntimeCapabilityRegistration createCapabilityRegistration(RuntimeCapability capability, Step step, String attribute) {
        CapabilityScope context = createCapabilityContext(step.address);
        RegistrationPoint rp = new RegistrationPoint(step.address, attribute);
        return new RuntimeCapabilityRegistration(capability, context, rp);
    }

    private CapabilityScope createCapabilityContext(PathAddress address) {
        return CapabilityScope.Factory.create(getProcessType(), address);
    }

    private <T> ServiceController<T> installService(ServiceBuilder<T> builder, ServiceName name, Step step)
            throws ServiceRegistryException, IllegalStateException {

        synchronized (realRemovingControllers) {
            boolean intr = false;
            try {
                boolean containsKey = realRemovingControllers.containsKey(name);
                long timeout = getBlockingTimeout().getLocalBlockingTimeout();
                long waitTime = timeout;
                long end = System.currentTimeMillis() + waitTime;
                while (containsKey && waitTime > 0) {
                    try {
                        realRemovingControllers.wait(waitTime);
                    } catch (InterruptedException e) {
                        intr = true;
                        if (respectInterruption) {
                            cancelled = true;
                            throw ControllerLogger.ROOT_LOGGER.serviceInstallCancelled();
                        } // else keep waiting and mark the thread interrupted at the end
                    }
                    containsKey = realRemovingControllers.containsKey(name);
                    waitTime = end - System.currentTimeMillis();
                }

                if (containsKey) {
                    // We timed out
                    throw ControllerLogger.ROOT_LOGGER.serviceInstallTimedOut(timeout / 1000, name);
                }

                // If a step removed this ServiceName before, it's no longer responsible
                // for any ill effect
                removalSteps.remove(name);

                ServiceController<T> controller = builder.install();
                step.serviceAdded(controller);
                return controller;
            } finally {
                if (intr) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Integration between ContextServiceTarget and the OperationContext that created it*/
    private interface ContextServiceBuilderSupplier {
        <T> ContextServiceBuilder<T> getContextServiceBuilder(final ServiceBuilder<T> delegate, ServiceName name);
    }

    /**
     * Service target that delegates to another target for most calls, but during execution of the
     * management op that created it does management specific integration and limits the available API.
     */
    private static class ContextServiceTarget extends DelegatingServiceTarget implements CapabilityServiceTarget {

        private final Set<ContextServiceBuilder> builders = new HashSet<>();
        private volatile ContextServiceBuilderSupplier builderSupplier;
        private final PathAddress targetAddress;

        ContextServiceTarget(final ServiceTarget delegate, final ContextServiceBuilderSupplier builderSupplier, final PathAddress targetAddress) {
            super(delegate);
            this.builderSupplier = builderSupplier;
            this.targetAddress = targetAddress;
        }

        /**
         * Signal that any management op that was executing when this target was created is done
         * and any future use of it should not integrate with that op nor be limited to authorized management uses.
         */
        synchronized void done() {
            builderSupplier = null;
            for (ContextServiceBuilder builder : builders) {
                builder.done();
            }
            builders.clear();
        }

        @Override
        public ServiceBuilder<?> addService(final ServiceName name) {
            final ServiceBuilder<?> realBuilder = super.getDelegate().addService(name);
            // If done() has been called we are no longer associated with a management op and should just
            // return the builder from delegate
            synchronized (this) {
                if (builderSupplier == null) {
                    return realBuilder;
                }
                ContextServiceBuilder<?> csb = builderSupplier.getContextServiceBuilder(realBuilder, name);
                builders.add(csb);
                return csb;
            }
        }

        @Override
        public <T> ServiceBuilder<T> addServiceValue(final ServiceName name, final Value<? extends Service<T>> value) {
            final ServiceBuilder<T> realBuilder = super.getDelegate().addServiceValue(name, value);
            // If done() has been called we are no longer associated with a management op and should just
            // return the builder from delegate
            synchronized (this) {
                if (builderSupplier == null) {
                    return realBuilder;
                }
                ContextServiceBuilder<T> csb = builderSupplier.getContextServiceBuilder(realBuilder, name);
                builders.add(csb);
                return csb;
            }
        }

        @Override
        public <T> CapabilityServiceBuilder<T> addService(final ServiceName name, final Service<T> service) throws IllegalArgumentException {
            return new CapabilityServiceBuilderImpl<>(addServiceValue(name, new ImmediateValue<>(service)), targetAddress);
        }

        @Override
        public <T> CapabilityServiceBuilder<T> addCapability(final RuntimeCapability<?> capability, final Service<T> service) throws IllegalArgumentException {
            if (capability.isDynamicallyNamed()){
                return addService(capability.getCapabilityServiceName(targetAddress), service);
            }else{
                return addService(capability.getCapabilityServiceName(), service);
            }
        }

        @Override
        protected ServiceTarget getDelegate() {
            checkNotInManagementOperation();
            return super.getDelegate();
        }

        private void checkNotInManagementOperation() {
            if (this.builderSupplier != null) {
                throw new UnsupportedOperationException(); // TODO most calls to this are things we could perhaps support.
            }
        }
    }

    /** Integration between ContextServiceBuilder and the OperationContext that created it*/
    private interface ContextServiceInstaller {
        <T> ServiceController<T> installService(ServiceBuilder<T> realBuilder);
        ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType, final PathAddress address);
    }

    /**
     * Service builder that integrates service installation work with overall execution of a management op.
     */
    private static class ContextServiceBuilder<T> extends DelegatingServiceBuilder<T> {

        private final ServiceBuilder<T> realBuilder;
        private volatile ContextServiceInstaller serviceInstaller;

        ContextServiceBuilder(final ServiceBuilder<T> realBuilder, final ContextServiceInstaller serviceInstaller) {
            super(realBuilder);
            this.realBuilder = realBuilder;
            this.serviceInstaller = serviceInstaller;
        }

        /**
         * Signal that any management op that was executing when this builder was created is done
         * and hence any future use of it should not involve integration with that op.
         */
        void done() {
            this.serviceInstaller = null;
        }

        public ServiceController<T> install() throws ServiceRegistryException, IllegalStateException {
            ContextServiceInstaller installer = this.serviceInstaller;
            return installer == null ? realBuilder.install() : installer.installService(realBuilder);
        }
        ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType, final PathAddress address){
            return this.serviceInstaller.getCapabilityServiceName(capabilityName, serviceType, address);
        }
    }

    /** Verifies that any service removals performed by this operation did not trigger a missing dependency */
    private class ServiceRemovalVerificationHandler implements OperationStepHandler {

        private final ContainerStateMonitor.ContainerStateChangeReport containerStateChangeReport;

        private ServiceRemovalVerificationHandler(ContainerStateMonitor.ContainerStateChangeReport containerStateChangeReport) {
            this.containerStateChangeReport = containerStateChangeReport;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {


            final Map<Step, Map<ServiceName, Set<ServiceName>>> missingByStep = new HashMap<Step, Map<ServiceName, Set<ServiceName>>>();
            // The realRemovingControllers map acts as the guard for the removalSteps map
            synchronized (realRemovingControllers) {
                for (Map.Entry<ServiceName, ContainerStateMonitor.MissingDependencyInfo> entry : containerStateChangeReport.getMissingServices().entrySet()) {
                    ContainerStateMonitor.MissingDependencyInfo missingDependencyInfo = entry.getValue();
                    Step removalStep = removalSteps.get(entry.getKey());
                    if (removalStep != null) {
                        Map<ServiceName, Set<ServiceName>> stepBadRemovals = missingByStep.get(removalStep);
                        if (stepBadRemovals == null) {
                            stepBadRemovals = new HashMap<ServiceName, Set<ServiceName>>();
                            missingByStep.put(removalStep, stepBadRemovals);
                        }
                        stepBadRemovals.put(entry.getKey(), missingDependencyInfo.getDependents());
                    }
                }
            }

            for (Map.Entry<Step, Map<ServiceName, Set<ServiceName>>> entry : missingByStep.entrySet()) {
                Step step = entry.getKey();
                if (!step.response.hasDefined(ModelDescriptionConstants.FAILURE_DESCRIPTION)) {
                    StringBuilder sb = new StringBuilder(ControllerLogger.ROOT_LOGGER.removingServiceUnsatisfiedDependencies());
                    for (Map.Entry<ServiceName, Set<ServiceName>> removed : entry.getValue().entrySet()) {
                        sb.append(ControllerLogger.ROOT_LOGGER.removingServiceUnsatisfiedDependencies(removed.getKey().getCanonicalName()));
                        boolean first = true;
                        for (ServiceName dependent : removed.getValue()) {
                            if (!first) {
                                sb.append(", ");
                            } else {
                                first = false;
                            }
                            sb.append(dependent);
                        }
                    }
                    step.response.get(ModelDescriptionConstants.FAILURE_DESCRIPTION).set(sb.toString());
                }
                // else a handler already recorded a failure; don't overwrite
            }

            if (!missingByStep.isEmpty()) {

                // Notify ContainerStateVerificationHandler that we've reported an issue so it
                // doesn't need to. (Even if we didn't report anything because the step already
                // had a failure message, we know there was a failure reported, which is what matters)
                context.attach(ContainerStateVerificationHandler.FAILURE_REPORTED_ATTACHMENT, Boolean.TRUE);

                if (context.isRollbackOnRuntimeFailure()) {
                    // We likely have altered the result of an already executed step,
                    // so it having a failure description won't trigger rollback the
                    // way a failure message normal does when the step completes execution.
                    // So, trigger rollback directly
                    context.setRollbackOnly();
                }
            }
            context.completeStep(RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    private static class OperationContextServiceRegistry extends DelegatingServiceRegistry {
        private final Set<OperationContextServiceController> controllers = Collections.synchronizedSet(new HashSet<>());
        private Step registryActiveStep;

        private OperationContextServiceRegistry(final ServiceRegistry registry, final Step registryActiveStep) {
            super(registry);
            this.registryActiveStep = registryActiveStep;
        }

        /**
         * Signal that any management op that was executing when this target was created is done
         * and any future use of it should not integrate with that op nor be limited to authorized management uses.
         */
        synchronized void done() {
            registryActiveStep = null;
            for (OperationContextServiceController controller : controllers) {
                controller.done();
            }
            controllers.clear();
        }

        @Override
        @SuppressWarnings("unchecked")
        public ServiceController<?> getRequiredService(final ServiceName serviceName) throws ServiceNotFoundException {
            ServiceController<?> result = getDelegate().getRequiredService(serviceName);
            synchronized (this) {
                if (registryActiveStep != null) {
                    OperationContextServiceController ocsc = new OperationContextServiceController(result, registryActiveStep);
                    result = ocsc;
                    controllers.add(ocsc);
                }
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ServiceController<?> getService(final ServiceName serviceName) {
            ServiceController<?> result = getDelegate().getService(serviceName);
            if (result != null) {
                synchronized (this) {
                    if (registryActiveStep != null) {
                        OperationContextServiceController ocsc = new OperationContextServiceController(result, registryActiveStep);
                        result = ocsc;
                        controllers.add(ocsc);
                    }
                }
            }
            return result;
        }
    }

    private static class OperationContextServiceController<S> extends DelegatingServiceController<S> {
        private volatile Step registryActiveStep;

        private OperationContextServiceController(final ServiceController<S> controller, final Step registryActiveStep) {
            super(controller);
            this.registryActiveStep = registryActiveStep;
        }

        void done() {
            this.registryActiveStep = null;
        }

        public boolean compareAndSetMode(final Mode expected, final Mode newMode) {
            checkModeTransition(newMode);
            boolean changed = getDelegate().compareAndSetMode(expected, newMode);
            Step step = registryActiveStep;
            if (changed && step != null) {
                step.serviceModeChanged(getDelegate());
            }
            return changed;
        }

        public void setMode(final Mode mode) {
            checkModeTransition(mode);
            getDelegate().setMode(mode);
            Step step = registryActiveStep;
            if (step != null) {
                step.serviceModeChanged(getDelegate());
            }
        }

        protected ServiceController<S> getDelegate() {
            return super.getDelegate();
        }

        private void checkModeTransition(final Mode mode) {
            if (mode == Mode.REMOVE) {
                throw ControllerLogger.ROOT_LOGGER.useOperationContextRemoveService();
            }
        }
    }

    private class AuthorizationResponseImpl implements ResourceAuthorization {

        private Map<Action.ActionEffect, AuthorizationResult> resourceResults = new HashMap<Action.ActionEffect, AuthorizationResult>();
        private Map<String, Map<Action.ActionEffect, AuthorizationResult>> attributeResults = new HashMap<String, Map<Action.ActionEffect, AuthorizationResult>>();
        private Map<String, AuthorizationResult> operationResults = new HashMap<String, AuthorizationResult>();
        private final TargetResource targetResource;
        private final Action standardAction;
        private volatile boolean attributesComplete = false;

        AuthorizationResponseImpl(Action standardAction, TargetResource targetResource) {
            this.standardAction = standardAction;
            this.targetResource = targetResource;
        }

        public AuthorizationResult getResourceResult(Action.ActionEffect actionEffect) {
            return resourceResults.get(actionEffect);
        }

        public AuthorizationResult getAttributeResult(String attribute, Action.ActionEffect actionEffect) {
            Map<Action.ActionEffect, AuthorizationResult> attrResults = attributeResults.get(attribute);
            return attrResults == null ? null : attrResults.get(actionEffect);
        }

        public AuthorizationResult getOperationResult(String operationName) {
            return operationResults.get(operationName);
        }

        private void addResourceResult(Action.ActionEffect actionEffect, AuthorizationResult result) {
            resourceResults.put(actionEffect, result);
        }

        private void addAttributeResult(String attribute, Action.ActionEffect actionEffect, AuthorizationResult result) {
            Map<Action.ActionEffect, AuthorizationResult> attrResults = attributeResults.get(attribute);
            if (attrResults == null) {
                attrResults = new HashMap<Action.ActionEffect, AuthorizationResult>();
                attributeResults.put(attribute, attrResults);
            }
            attrResults.put(actionEffect, result);
        }

        private void addOperationResult(String operationName, AuthorizationResult result) {
            operationResults.put(operationName, result);
        }

        private AuthorizationResult validateAddAttributeEffects(String operationName, Set<ActionEffect> actionEffects, ModelNode operation) {
            AuthorizationResult basic = operationResults.get(operationName);
            assert basic != null : " no basic authorization has been performed for operation 'add'";
            if (basic.getDecision() == Decision.DENY) {
                ImmutableManagementResourceRegistration resourceRegistration = targetResource.getResourceRegistration();
                ModelNode model = targetResource.getResource().getModel();
                boolean attributeWasDenied = false;
                for (Map.Entry<String, Map<ActionEffect, AuthorizationResult>> attributeResultEntry : attributeResults.entrySet()) {
                    String attrName = attributeResultEntry.getKey();
                    boolean attrDenied = isAttributeDenied(attributeResultEntry.getValue(), actionEffects);
                    if (attrDenied) {
                        attributeWasDenied = true;
                        // See if we even care about this attribute
                        // BES 2014/11/11 -- currently "model" is always undefined at this point, so testing it is silly,
                        // but I leave it in here as a 2nd line of defense in case we rework something and this check
                        // gets run after model gets populated
                        if (operation.hasDefined(attrName) || model.hasDefined(attrName) ||
                                isAddableAttribute(attrName, resourceRegistration)) {
                            Map<ActionEffect, AuthorizationResult> attrResults = attributeResultEntry.getValue();
                            for (ActionEffect actionEffect : actionEffects) {
                                AuthorizationResult authResult = attrResults.get(actionEffect);
                                if (authResult.getDecision() == AuthorizationResult.Decision.DENY) {
                                    addOperationResult(operationName, authResult);
                                    return authResult;
                                }
                            }
                        }
                    }
                }
                if (attributeWasDenied) {
                    // An attribute had been denied, but we didn't return, so, the attribute must not have been required
                    addOperationResult(operationName, AuthorizationResult.PERMITTED);
                    return AuthorizationResult.PERMITTED;
                }
            }
            return basic;
        }

        private boolean isAttributeDenied(Map<ActionEffect, AuthorizationResult> attributeResults, Set<ActionEffect> actionEffects) {
            for (ActionEffect actionEffect : actionEffects) {
                AuthorizationResult ar = attributeResults.get(actionEffect);
                if (ar != null && ar.getDecision() == Decision.DENY) {
                    return true;
                }
            }
            return false;
        }

        private boolean isAddableAttribute(String attrName, ImmutableManagementResourceRegistration resourceRegistration) {
            AttributeAccess attributeAccess = resourceRegistration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attrName);
            if (attributeAccess == null) {
                return false;
            }
            if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION
                    && attributeAccess.getAccessType() == AttributeAccess.AccessType.READ_WRITE) {
                AttributeDefinition ad = attributeAccess.getAttributeDefinition();
                if (ad == null) {
                    return false;
                }
                if (ad.isRequired() || ad.isNullSignificant()) {
                    // must be set, presence of a default means not setting is the same as setting,
                    // or the AD is specifically configured that null is significant
                    return true;
                }
            }
            return false;
        }

    }

    private class ActiveOperationResource extends PlaceholderResource.PlaceholderResourceEntry implements Cancellable {

        private ActiveOperationResource() {
            super(ACTIVE_OPERATION, operationId.toString());
        }

        @Override
        public boolean isModelDefined() {
            return true;
        }

        @Override
        public ModelNode getModel() {
            final ModelNode model = new ModelNode();

            model.get(OP).set(operationName);
            model.get(OP_ADDR).set(operationAddress);

            model.get(CALLER_THREAD).set(initiatingThread.getName());
            ModelNode accessMechanismNode = model.get(ACCESS_MECHANISM);
            ModelNode domainUUIDNode = model.get(DOMAIN_UUID);
            boolean domainRollout = false;
            if (accessAuditContext != null) {
                AccessMechanism accessMechanism = accessAuditContext.getAccessMechanism();
                if (accessMechanism != null) {
                    accessMechanismNode.set(accessMechanism.toString());
                }
                String domainUUID = accessAuditContext.getDomainUuid();
                if (domainUUID != null) {
                    domainUUIDNode.set(domainUUID);
                }
                domainRollout = accessAuditContext.isDomainRollout();
            }
            model.get(DOMAIN_ROLLOUT).set(domainRollout);
            model.get(EXECUTION_STATUS).set(getExecutionStatus());
            model.get(RUNNING_TIME).set(System.nanoTime() - startTime);
            long exclusive = exclusiveStartTime;
            if (exclusive > -1) {
                exclusive = System.nanoTime() - exclusive;
            }
            model.get(EXCLUSIVE_RUNNING_TIME).set(exclusive);
            model.get(CANCELLED).set(cancelled);
            return model;
        }

        private String getExecutionStatus() {
            ExecutionStatus currentStatus = executionStatus;
            if (currentStatus == ExecutionStatus.EXECUTING) {
                currentStatus = resultAction == ResultAction.ROLLBACK ? ExecutionStatus.ROLLING_BACK
                        : currentStage == Stage.DONE ? ExecutionStatus.COMPLETING : ExecutionStatus.EXECUTING;
            }
            return currentStatus.toString();
        }

        @Override
        public boolean cancel() {
            synchronized (done) {
                boolean canCancel = !done.done;
                if (canCancel) {
                    done.done = true;
                    ControllerLogger.MGMT_OP_LOGGER.cancellingOperation(operationName, operationId, initiatingThread.getName());
                    initiatingThread.interrupt();
                }
                return canCancel;
            }
        }
    }

    private static class BooleanHolder {
        private boolean done = false;
    }

    private static class CapabilityServiceSupportImpl implements CapabilityServiceSupport {
        private final ManagementModel managementModel;

        private CapabilityServiceSupportImpl(ManagementModel managementModel) {
            this.managementModel = managementModel;
        }

        @Override
        public boolean hasCapability(String capabilityName) {
            return managementModel.getCapabilityRegistry().hasCapability(capabilityName, CapabilityScope.GLOBAL);
        }

        @Override
        public <T> T getCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) throws NoSuchCapabilityException {
            try {
                return managementModel.getCapabilityRegistry().getCapabilityRuntimeAPI(capabilityName, CapabilityScope.GLOBAL, apiType);
            } catch (IllegalStateException e) {
                throw new NoSuchCapabilityException(capabilityName);
            }
        }

        @Override
        public <T> T getCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) throws NoSuchCapabilityException {
            String fullName = RuntimeCapability.buildDynamicCapabilityName(capabilityBaseName, dynamicPart);
            return getCapabilityRuntimeAPI(fullName, apiType);
        }

        @Override
        public <T> Optional<T> getOptionalCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) {
            try {
                return Optional.of(getCapabilityRuntimeAPI(capabilityName, apiType));
            } catch (NoSuchCapabilityException e) {
                return Optional.empty();
            }
        }

        @Override
        public <T> Optional<T> getOptionalCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) {
            try {
                return Optional.of(getCapabilityRuntimeAPI(capabilityBaseName, dynamicPart, apiType));
            } catch (NoSuchCapabilityException e) {
                return Optional.empty();
            }
        }

        @Override
        public ServiceName getCapabilityServiceName(String capabilityName) {
            try {
                return managementModel.getCapabilityRegistry().getCapabilityServiceName(capabilityName, CapabilityScope.GLOBAL, null);
            } catch (IllegalStateException | IllegalArgumentException ignore) {
                // ignore
            }
            ControllerLogger.ROOT_LOGGER.debugf("CapabilityServiceSupport: Parsing ServiceName for %s", capabilityName);
            return ServiceNameFactory.parseServiceName(capabilityName);
        }

        @Override
        public ServiceName getCapabilityServiceName(String capabilityBaseName, String ... dynamicPart) {
            return getCapabilityServiceName(capabilityBaseName).append(dynamicPart);
        }
    }


    private static class CapabilityServiceBuilderImpl<T> extends DelegatingServiceBuilder<T> implements CapabilityServiceBuilder<T> {
        private final PathAddress targetAddress;
        CapabilityServiceBuilderImpl(ServiceBuilder<T> delegate, PathAddress targetAddress) {
            super(delegate);
            this.targetAddress = targetAddress;
        }

        public <I> CapabilityServiceBuilder<T> addCapabilityRequirement(String capabilityBaseName, Class<I> serviceType, Injector<I> target, String... referenceNames) {
            String capabilityName = RuntimeCapability.buildDynamicCapabilityName(capabilityBaseName, referenceNames);
            final ServiceName serviceName = getCapabilityServiceName(capabilityName, serviceType);
            addDependency(serviceName, serviceType, target);
            return this;
        }

        @Override
        public <I> CapabilityServiceBuilder<T> addCapabilityRequirement(String capabilityName, Class<I> type, Injector<I> target) {
            final ServiceName serviceName = getCapabilityServiceName(capabilityName, type);
            addDependency(serviceName, type, target);
            return this;
        }


        @Override
        public <I> CapabilityServiceBuilder<T> addCapabilityRequirement(String capabilityName, Class<I> type) {
            final ServiceName serviceName = getCapabilityServiceName(capabilityName, type);
            requires(serviceName);
            return this;
        }

        @Override
        public <I> CapabilityServiceBuilder<T> addDependency(ServiceName dependency, Class<I> type, Injector<I> target){
            super.addDependency(dependency, type, target);
            return this;
        }

        @Override
        public CapabilityServiceBuilder<T> setInitialMode(ServiceController.Mode mode) {
            super.setInitialMode(mode);
            return this;
        }

        @Override
        public <V> Supplier<V> requiresCapability(String capabilityBaseName, Class<V> dependencyType, String... referenceNames) {
            String capabilityName;
            if (referenceNames != null && referenceNames.length > 0) {
                capabilityName = RuntimeCapability.buildDynamicCapabilityName(capabilityBaseName, referenceNames);
            } else {
                capabilityName = capabilityBaseName;
            }
            final ServiceName serviceName = getCapabilityServiceName(capabilityName, dependencyType);
            return requires(serviceName);
        }

        @Override
        public <I> CapabilityServiceBuilder<T> addInjection(Injector<? super I> target, I value) {
            super.addInjection(target, value);
            return this;
        }

        private ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType) {
            return getCapabilityServiceName(capabilityName, serviceType, targetAddress);
        }

        private ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType, final PathAddress address) {
            return ((ContextServiceBuilder<T>)getDelegate()).getCapabilityServiceName(capabilityName, serviceType, address);
        }
    }

}
