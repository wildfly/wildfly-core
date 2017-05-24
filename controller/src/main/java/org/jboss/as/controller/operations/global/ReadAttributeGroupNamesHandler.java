/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
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
