/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.mgmt.DomainControllerRuntimeIgnoreTransformationRegistry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Emanuel Muckenhuber
 */
public class ReadMasterDomainOperationsHandler implements OperationStepHandler {

    private static final PathAddressFilter FILTER = new PathAddressFilter(true);
    public static final OperationStepHandler INSTANCE = new ReadMasterDomainOperationsHandler();
    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("model-operations", ControllerResolver.getResolver(SUBSYSTEM))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .setPrivateEntry()
            .build();

    static {
        FILTER.addReject(PathAddress.pathAddress(PathElement.pathElement(EXTENSION)));
        FILTER.addReject(PathAddress.pathAddress(PathElement.pathElement(HOST)));
    }

    ReadMasterDomainOperationsHandler() {
        //
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();
        context.attach(PathAddressFilter.KEY, FILTER);
        context.addStep(operation, GenericModelDescribeOperationHandler.INSTANCE, OperationContext.Stage.MODEL);
        context.stepCompleted();
    }

    static List<ModelNode> transformOperations(final Transformers transformers, final DomainControllerRuntimeIgnoreTransformationRegistry ignoredDomainResourceRegistry) {
        return null;
    }

}
