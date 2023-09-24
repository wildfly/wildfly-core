/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALL_SERVICES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOURCE_SERVICES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_ONLY;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.LOCALE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.NAME;

import java.util.Locale;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.AuthorizationResult.Decision;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} returning the type description of a single operation description.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ReadOperationDescriptionHandler implements OperationStepHandler {

    static final SimpleAttributeDefinition ACCESS_CONTROL = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ACCESS_CONTROL, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();


    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_OPERATION_DESCRIPTION_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(NAME, LOCALE, ACCESS_CONTROL)
            .setReplyType(ModelType.OBJECT)
            .setReadOnly()
            .build();

    static final OperationStepHandler INSTANCE = new ReadOperationDescriptionHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        String operationName = NAME.resolveModelAttribute(context, operation).asString();
        boolean accessControl = ACCESS_CONTROL.resolveModelAttribute(context, operation).asBoolean();

        final DescribedOp describedOp = getDescribedOp(context, operationName, operation, !accessControl);
        if (describedOp == null || (context.getProcessType() == ProcessType.DOMAIN_SERVER &&
                !(describedOp.flags.contains(OperationEntry.Flag.RUNTIME_ONLY) || describedOp.flags.contains(OperationEntry.Flag.READ_ONLY)))) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.operationNotRegistered(operationName, context.getCurrentAddress()));
        } else {
            ModelNode result = describedOp.getDescription();

            if (accessControl) {
                final PathAddress address = context.getCurrentAddress();
                ModelNode operationToCheck = Util.createOperation(operationName, address);
                operationToCheck.get(OPERATION_HEADERS).set(operation.get(OPERATION_HEADERS));
                AuthorizationResult authorizationResult = context.authorizeOperation(operationToCheck);
                result.get(ACCESS_CONTROL.getName(), EXECUTE).set(authorizationResult.getDecision() == Decision.PERMIT);

            }

            context.getResult().set(result);
        }
    }

    private static DescribedOp getDescribedOp(OperationContext context, String operationName, ModelNode operation, boolean lenient) throws OperationFailedException {
        DescribedOp result = null;
        OperationEntry operationEntry;
        // First try to get the current resource registration to give authz a chance to reject this request
        ImmutableManagementResourceRegistration registry = context.getResourceRegistration();
        if (registry != null) {
            operationEntry = registry.getOperationEntry(PathAddress.EMPTY_ADDRESS, operationName);
        } else {
            // We know the user is authorized to read this address.
            // There's no MRR at that address, but see if the MRR tree can resolve an operation entry (e.g. an inherited one)
            operationEntry = context.getRootResourceRegistration().getOperationEntry(context.getCurrentAddress(), operationName);
        }

        if (operationEntry != null) {
            Locale locale = GlobalOperationHandlers.getLocale(context, operation);
            result = new DescribedOp(operationEntry, locale);
        } else if (lenient) {
            // For wildcard elements, check specific registrations where the same OSH is used
            // for all such registrations
            PathAddress address = context.getCurrentAddress();
            if (address.size() > 0) {
                PathElement pe = address.getLastElement();
                if (pe.isWildcard()) {
                    ImmutableManagementResourceRegistration rootRegistration = context.getRootResourceRegistration();
                    String type = pe.getKey();
                    PathAddress parent = address.subAddress(0, address.size() - 1);
                    Set<PathElement> children = rootRegistration.getChildAddresses(parent);
                    if (children != null) {
                        Locale locale = GlobalOperationHandlers.getLocale(context, operation);
                        DescribedOp found = null;
                        for (PathElement child : children) {
                            if (type.equals(child.getKey())) {
                                OperationEntry oe = rootRegistration.getOperationEntry(parent.append(child), operationName);
                                DescribedOp describedOp = oe == null ? null : new DescribedOp(oe, locale);
                                if (describedOp == null || (found != null && !found.equals(describedOp))) {
                                    // Not all children have the same handler; give up
                                    found = null;
                                    break;
                                }
                                // We have a candidate OSH
                                found = describedOp;
                            }
                        }
                        result = found;
                    }
                }

            }
        }
        return result;
    }

    static class DescribedOp {
        private final ModelNode description;
        private final Set<OperationEntry.Flag> flags;

        DescribedOp(OperationEntry operationEntry, Locale locale) {
            this.description = operationEntry.getDescriptionProvider().getModelDescription(locale);
            this.flags = operationEntry.getFlags();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DescribedOp that = (DescribedOp) o;

            return description.equals(that.description) && flags.equals(that.flags);

        }

        @Override
        public int hashCode() {
            int result = description.hashCode();
            result = 31 * result + flags.hashCode();
            return result;
        }

        /**
         * @return the description of the operation augmented of the operation's entry flags.
         */
        ModelNode getDescription() {
            final ModelNode result = description.clone();
            boolean readOnly = flags.contains(OperationEntry.Flag.READ_ONLY);
            result.get(READ_ONLY).set(readOnly);
            if (!readOnly) {
                if (flags.contains(OperationEntry.Flag.RESTART_ALL_SERVICES)) {
                    result.get(RESTART_REQUIRED).set(ALL_SERVICES);
                } else if (flags.contains(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)) {
                    result.get(RESTART_REQUIRED).set(RESOURCE_SERVICES);
                } else if (flags.contains(OperationEntry.Flag.RESTART_JVM)) {
                    result.get(RESTART_REQUIRED).set(JVM);
                }
            }

            boolean runtimeOnly = flags.contains(OperationEntry.Flag.RUNTIME_ONLY);
            result.get(RUNTIME_ONLY).set(runtimeOnly);
            return result;
        }
    }
}
