/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIBE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Outputs the profile as a series of operations needed to construct the profile
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class ProfileDescribeHandler extends GenericModelDescribeOperationHandler {

    public static final ProfileDescribeHandler INSTANCE = new ProfileDescribeHandler();

    public static final AttributeDefinition SERVER_LAUNCH = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SERVER_LAUNCH, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    private static final Set<Action.ActionEffect> DESCRIBE_EFFECTS =
            Collections.unmodifiableSet(EnumSet.of(Action.ActionEffect.ADDRESS, Action.ActionEffect.READ_CONFIG));

    private ProfileDescribeHandler() {
        super(DESCRIBE, true);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final AuthorizationResult authResult = context.authorize(operation, DESCRIBE_EFFECTS);
        authResult.failIfDenied(operation, context.getCurrentAddress());

        // WFCORE-1353. If this op is being used as part of a server launch, pass that info
        // to any subsystem describe handlers.
        if (SERVER_LAUNCH.resolveModelAttribute(context, operation).asBoolean()) {
            context.attach(GenericSubsystemDescribeHandler.SERVER_LAUNCH_KEY, Boolean.TRUE);
        }

        super.execute(context, operation);
    }

    @Override
    protected void processMore(OperationContext context, ModelNode operation, Resource resource, PathAddress address, Map<String, ModelNode> includeResults) throws OperationFailedException {
        final ModelNode profile = resource.getModel();
        if (profile.hasDefined(INCLUDES)) {
            // Call this op for each included profile
            for (ModelNode include : profile.get(INCLUDES).asList()) {

                final String includeName = include.asString();
                final ModelNode includeRsp = new ModelNode();
                includeResults.put(includeName, includeRsp);

                final ModelNode includeAddress = address.subAddress(0, address.size() - 1).append(PathElement.pathElement(PROFILE, includeName)).toModelNode();
                final ModelNode newOp = operation.clone();
                newOp.get(OP_ADDR).set(includeAddress);

                context.addStep(includeRsp, newOp, INSTANCE, OperationContext.Stage.MODEL, true);
            }
        }
    }

}
