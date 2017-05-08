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

package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIBE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A generic handler recursively creating add operations for a managed resource using it's
 * attributes as the request-parameters.
 *
 * @author Emanuel Muckenhuber
 */
public class GenericSubsystemDescribeHandler implements OperationStepHandler, DescriptionProvider {

    public static final GenericSubsystemDescribeHandler INSTANCE = new GenericSubsystemDescribeHandler();
    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(DESCRIBE, ControllerResolver.getResolver(SUBSYSTEM))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .withFlag(OperationEntry.Flag.HIDDEN)
            .build();

    public static final Set<Action.ActionEffect> DESCRIBE_EFFECTS =
            Collections.unmodifiableSet(EnumSet.of(Action.ActionEffect.ADDRESS, Action.ActionEffect.READ_CONFIG));

    /** Operation attachment key used when the describe operation is being invoked in order to create the operations to launch a server */
    public static final OperationContext.AttachmentKey<Boolean> SERVER_LAUNCH_KEY = OperationContext.AttachmentKey.create(Boolean.class);

    private final Comparator<PathElement> comparator;

    protected GenericSubsystemDescribeHandler() {
        this(null);
    }

    /**
     * Creates a new describe handler.
     * <p/>
     * If the comparator is <b>not</b> {@code null} as the handler describes children of a resource the order in which
     * those children are described is determined using the comparator. This allows the result to order the
     * add operations for the child resources.
     * <p/>
     * If the comparator is {@code null} the order for the child resources is not guaranteed, other than that all
     * children of a given type will be processed in the order of child names returned by {@link Resource#getChildren(String)}
     * invoked on the parent.
     *
     * @param comparator the comparator used to sort the child addresses
     */
    protected GenericSubsystemDescribeHandler(final Comparator<PathElement> comparator) {
        this.comparator = comparator;
    }

    /**
     * Creates a new describe handler.
     * <p/>
     * If the comparator is <b>not</b> {@code null} as the handler describes children of a resource the order in which
     * those children are described is determined using the comparator. This allows the result to order the
     * add operations for the child resources.
     * <p/>
     * If the comparator is {@code null} the order for the child resources is not guaranteed, other than that all
     * children of a given type will be processed in the order of child names returned by {@link Resource#getChildren(String)}
     * invoked on the parent.
     *
     * @param comparator the comparator used to sort the child addresses
     */
    public static GenericSubsystemDescribeHandler create(final Comparator<PathElement> comparator) {
        return new GenericSubsystemDescribeHandler(comparator);
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final ModelNode address;
        final PathAddress pa = context.getCurrentAddress();

        AuthorizationResult authResult = context.authorize(operation, DESCRIBE_EFFECTS);
        if (authResult.getDecision() != AuthorizationResult.Decision.PERMIT) {
            throw ControllerLogger.ROOT_LOGGER.unauthorized(operation.require(OP).asString(), pa, authResult.getExplanation());
        }

        if (pa.size() > 0) {
            address = new ModelNode().add(pa.getLastElement().getKey(), pa.getLastElement().getValue());
        } else {
            address = new ModelNode().setEmptyList();
        }
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode result = context.getResult();
        describe(context.getAttachment(OrderedChildTypesAttachment.KEY), resource,
                address, result, context.getResourceRegistration());
    }

    protected void describe(final OrderedChildTypesAttachment orderedChildTypesAttachment, final Resource resource,
                            final ModelNode address, ModelNode result, final ImmutableManagementResourceRegistration registration) {
        if(resource == null || registration.isRemote() || registration.isRuntimeOnly() || resource.isProxy() || resource.isRuntime() || registration.isAlias()) {
            return;
        }

        final Set<PathElement> children;
        final Set<PathElement> defaultChildren = new LinkedHashSet<>();
        for (String type : resource.getChildTypes()) {
            for (String value : resource.getChildrenNames(type)) {
                defaultChildren.add(PathElement.pathElement(type, value));
            }
        }
        if (comparator == null) {
            children = defaultChildren;
        } else {
            children = new TreeSet<PathElement>(comparator);
            children.addAll(defaultChildren);
        }
        result.add(createAddOperation(orderedChildTypesAttachment, address, resource, children));
        for(final PathElement element : children) {
            final Resource child = resource.getChild(element);
            final ImmutableManagementResourceRegistration childRegistration = registration.getSubModel(PathAddress.pathAddress(element));
            if (childRegistration == null) {
                ControllerLogger.ROOT_LOGGER.debugf("No MRR exists for %s", registration.getPathAddress().append(element));
            } else {
                final ModelNode childAddress = address.clone();
                childAddress.add(element.getKey(), element.getValue());
                describe(orderedChildTypesAttachment, child, childAddress, result, childRegistration);
            }
        }
    }

    protected ModelNode createAddOperation(final OrderedChildTypesAttachment orderedChildTypesAttachment,
                                           final ModelNode address, final Resource resource, final Set<PathElement> children) {
        ModelNode addOp = createAddOperation(address, resource.getModel(), children);
        if (orderedChildTypesAttachment != null) {
            orderedChildTypesAttachment.addOrderedChildResourceTypes(PathAddress.pathAddress(address), resource);
        }
        return addOp;
    }

    protected ModelNode createAddOperation(final ModelNode address, final ModelNode subModel, final Set<PathElement> children) {
        final ModelNode operation = subModel.clone();
        operation.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
        operation.get(ModelDescriptionConstants.OP_ADDR).set(address);
        if(children != null && ! children.isEmpty()) {
            for(final PathElement path : children) {
                if(subModel.hasDefined(path.getKey())) {
                    subModel.remove(path.getKey());
                }
            }
        }
        return operation;
    }

    /**
     *
     * @param locale the locale to use to generate any localized text used in the description.
     *               May be {@code null}, in which case {@link Locale#getDefault()} should be used
     *
     * @return definition of operation
     * @deprecated use {@link #DEFINITION} for registration of operation
     */
    @Override
    public ModelNode getModelDescription(Locale locale) {
        // This is a private operation, so we should not be getting requests for descriptions
        return DEFINITION.getDescriptionProvider().getModelDescription(locale);
    }
}
