/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_GROUP_NAMES_OPERATION;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} returning the names of attribute groups of a given model
 * address.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ReadAttributeGroupNamesHandler implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_ATTRIBUTE_GROUP_NAMES_OPERATION, ControllerResolver.getResolver("global"))
            .setReadOnly()
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.STRING)
            .build();

    static OperationStepHandler INSTANCE = new ReadAttributeGroupNamesHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode result = context.getResult().setEmptyList();
        final ImmutableManagementResourceRegistration registry = context.getResourceRegistration();
        final Set<String> attributeNames = registry != null ? registry.getAttributeNames(PathAddress.EMPTY_ADDRESS) : Collections.<String>emptySet();
        TreeSet<String> groupNames = new TreeSet<String>();
        for (final String attributeName : attributeNames) {
            final AttributeAccess attribute = registry.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
            if (attribute.getAttributeDefinition().getAttributeGroup() != null) {
                groupNames.add(attribute.getAttributeDefinition().getAttributeGroup());
            }
        }
        for (String groupName : groupNames) {
            result.add(groupName);
        }
    }
}
