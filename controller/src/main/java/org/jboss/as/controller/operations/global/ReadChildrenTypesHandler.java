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

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_TYPES_OPERATION;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_ALIASES;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_SINGLETONS;

import java.util.Set;
import java.util.TreeSet;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;



/**
 * {@link org.jboss.as.controller.OperationStepHandler} querying the child types of a given node.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ReadChildrenTypesHandler implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_CHILDREN_TYPES_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(INCLUDE_ALIASES, INCLUDE_SINGLETONS)
            .setReadOnly()
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.STRING)
            .build();

    static final OperationStepHandler INSTANCE = new ReadChildrenTypesHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ImmutableManagementResourceRegistration registry = context.getResourceRegistration();
        if (registry == null) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noSuchResourceType(context.getCurrentAddress()));
        }
        final boolean aliases = INCLUDE_ALIASES.resolveModelAttribute(context, operation).asBoolean(false);
        final boolean singletons = INCLUDE_SINGLETONS.resolveModelAttribute(context, operation).asBoolean(false);
        Set<PathElement> childTypes = registry.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        Set<String> children = new TreeSet<String>();
        for (final PathElement child : childTypes) {
            PathAddress relativeAddr = PathAddress.pathAddress(child);
            ImmutableManagementResourceRegistration childReg = registry.getSubModel(relativeAddr);
            boolean isSingletonResource = childReg == null || !child.isWildcard();
            if (childReg.isAlias() && !aliases) {
                continue;
            }
            if(singletons && isSingletonResource)  {
                children.add(child.getKey() + '=' + child.getValue());
            } else {
                children.add(child.getKey());
            }
        }
        final ModelNode result = context.getResult();
        result.setEmptyList();
        for(String child : children) {
           result.add(child);
        }
    }
}
