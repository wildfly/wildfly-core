/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACTIVE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_ROLLOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCLUSIVE_RUNNING_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTION_STATUS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_TIME;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} that looks for and returns the id of single operation that is in
 * execution status {@link org.jboss.as.controller.OperationContext.ExecutionStatus#AWAITING_STABILITY}
 * and has been executing in that status for longer than a specified {@code timeout} seconds.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class FindNonProgressingOperationHandler implements OperationStepHandler {

    private static final AttributeDefinition STABILITY_TIMEOUT = SimpleAttributeDefinitionBuilder.create("timeout", ModelType.INT)
            .setRequired(false)
            .setDefaultValue(new ModelNode(15))
            .setValidator(new IntRangeValidator(0, true))
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .build();

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("find-non-progressing-operation",
            DomainManagementResolver.getResolver(CORE, MANAGEMENT_OPERATIONS))
            .setReplyType(ModelType.STRING)
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setReadOnly()
            .setRuntimeOnly()
            .build();

    static final OperationStepHandler INSTANCE = new FindNonProgressingOperationHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final long timeout = TimeUnit.SECONDS.toNanos(STABILITY_TIMEOUT.resolveModelAttribute(context, operation).asLong());

        DomainManagementLogger.ROOT_LOGGER.debugf("Identification of operation not progressing after [%d] ns has been requested", timeout);

        String nonProgressing = findNonProgressingOp(context, timeout);
        ModelNode result = context.getResult();
        if (nonProgressing != null) {
            result.set(nonProgressing);
        }
    }

    static String findNonProgressingOp(OperationContext context, long timeout) throws OperationFailedException {
        return findNonProgressingOp(context.readResource(PathAddress.EMPTY_ADDRESS), context.getProcessType().isServer(), timeout);
    }

    // Separate from other findNonProgressingOp variant to allow unit testing without needing a mock OperationContext
    static String findNonProgressingOp(Resource resource, boolean forServer, long timeout) throws OperationFailedException {

        Resource.ResourceEntry nonProgressing = null;
        for (Resource.ResourceEntry child : resource.getChildren(ACTIVE_OPERATION)) {
            ModelNode model = child.getModel();
            if (model.get(EXCLUSIVE_RUNNING_TIME).asLong() > timeout) {
                nonProgressing = child;
                ControllerLogger.MGMT_OP_LOGGER.tracef("non-progressing op: %s", nonProgressing.getModel());
                break;
            }
        }
        if (nonProgressing != null && !forServer) {
            // WFCORE-263
            // See if the op is non-progressing because it's the HC op waiting for commit
            // from the DC while other ops (i.e. ops proxied to our servers) associated
            // with the same domain-uuid are not completing
            ModelNode model = nonProgressing.getModel();
            if (model.get(DOMAIN_ROLLOUT).asBoolean()
                    && OperationContext.ExecutionStatus.COMPLETING.toString().equals(model.get(EXECUTION_STATUS).asString())
                    && model.hasDefined(DOMAIN_UUID)) {
                ControllerLogger.MGMT_OP_LOGGER.trace("Potential domain rollout issue");
                String domainUUID = model.get(DOMAIN_UUID).asString();

                Set<String> relatedIds = null;
                List<Resource.ResourceEntry> relatedExecutingOps = null;
                for (Resource.ResourceEntry activeOp : resource.getChildren(ACTIVE_OPERATION)) {
                    if (nonProgressing.getName().equals(activeOp.getName())) {
                        continue; // ignore self
                    }
                    ModelNode opModel = activeOp.getModel();
                    if (opModel.hasDefined(DOMAIN_UUID) && domainUUID.equals(opModel.get(DOMAIN_UUID).asString())
                            && opModel.get(RUNNING_TIME).asLong() > timeout) {
                        if (relatedIds == null) {
                            relatedIds = new TreeSet<String>(); // order these as an aid to unit testing
                        }
                        relatedIds.add(activeOp.getName());

                        // If the op is ExecutionStatus.EXECUTING that means it's still EXECUTING on the
                        // server or a prepare message got lost. It would be COMPLETING if the server
                        // had sent a prepare message, as that would result in ProxyStepHandler calling completeStep
                        if (OperationContext.ExecutionStatus.EXECUTING.toString().equals(opModel.get(EXECUTION_STATUS).asString())) {
                            if (relatedExecutingOps == null) {
                                relatedExecutingOps = new ArrayList<Resource.ResourceEntry>();
                            }
                            relatedExecutingOps.add(activeOp);
                            ControllerLogger.MGMT_OP_LOGGER.tracef("Related executing: %s", opModel);
                        } else ControllerLogger.MGMT_OP_LOGGER.tracef("Related non-executing: %s", opModel);
                    } else ControllerLogger.MGMT_OP_LOGGER.tracef("unrelated: %s", opModel);
                }

                if (relatedIds != null) {
                    // There are other ops associated with this domain-uuid that are also not completing
                    // in the desired time, so we can't treat the one holding the lock as the problem.
                    if (relatedExecutingOps != null && relatedExecutingOps.size() == 1) {
                        // There's a single related op that's executing for too long. So we can report that one.
                        // Note that it's possible that the same problem exists on other hosts as well
                        // and that this cancellation will not resolve the overall problem. But, we only
                        // get here on a slave HC and if the user is invoking this on a slave and not the
                        // master, we'll assume they have a reason for doing that and want us to treat this
                        // as a problem on this particular host.
                        nonProgressing = relatedExecutingOps.get(0);
                    } else {
                        // Fail and provide a useful failure message.
                        throw DomainManagementLogger.ROOT_LOGGER.domainRolloutNotProgressing(nonProgressing.getName(),
                                timeout, domainUUID, relatedIds);
                    }
                }
            }
        }

        return  nonProgressing == null ? null : nonProgressing.getName();
    }
}
