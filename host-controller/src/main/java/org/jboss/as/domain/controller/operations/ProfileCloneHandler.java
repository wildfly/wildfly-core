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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLONE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.resources.DomainResolver;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Emanuel Muckenhuber
 */
public class ProfileCloneHandler implements OperationStepHandler {

    private static final AttributeDefinition TO_PROFILE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.TO_PROFILE, ModelType.STRING).build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(CLONE, DomainResolver.getResolver(PROFILE, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .addParameter(TO_PROFILE)
            // .setPrivateEntry()
            .build();

    private final LocalHostControllerInfo hostInfo;
    private final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;

    public ProfileCloneHandler(LocalHostControllerInfo hostInfo, IgnoredDomainResourceRegistry ignoredDomainResourceRegistry) {
        this.hostInfo = hostInfo;
        this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final String profileName = context.getCurrentAddressValue();
        final String newProfile = TO_PROFILE.resolveModelAttribute(context, operation).asString();
        final String operationName = GenericModelDescribeOperationHandler.DEFINITION.getName();
        final PathAddress address = PathAddress.pathAddress(PathElement.pathElement(PROFILE, profileName));
        final PathAddress newPA = PathAddress.pathAddress(PROFILE, newProfile);

        if (!hostInfo.isMasterDomainController()) {
            if (ignoredDomainResourceRegistry.isResourceExcluded(address) || ignoredDomainResourceRegistry.isResourceExcluded(newPA)) {
                return;
            }

            if (hostInfo.isRemoteDomainControllerIgnoreUnaffectedConfiguration()) {
                //Since the cloned profile is a new one, we don't need to do anything. It is guaranteed to be unused,
                //and thus should be ignored
                return;
            }
        }

        final ModelNode describeOp = new ModelNode();
        describeOp.get(OP).set(operationName);
        describeOp.get(OP_ADDR).set(address.toModelNode());

        final ModelNode result = new ModelNode();

        final OperationStepHandler handler = context.getRootResourceRegistration().getOperationHandler(address, operationName);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final PathAddress newPA = PathAddress.pathAddress(PROFILE, newProfile);
                final List<ModelNode> operations = new ArrayList<>(result.get(RESULT).asList());

                //Make sure that we add everything in exactly the same order as the original profile
                Map<String, List<ModelNode>> opsBySubsystem = new LinkedHashMap<>();
                ModelNode profileAdd = null;

                for (ModelNode op : operations) {
                    PathAddress addr = PathAddress.pathAddress(op.require(OP_ADDR)).subAddress(1);

                    //Adjust the address for the new profile
                    final PathAddress a = newPA.append(addr);
                    op.get(OP_ADDR).set(a.toModelNode());

                    if (addr.size() == 0) {
                        profileAdd = op;
                    } else {
                        String subsystem = addr.getElement(0).getValue();
                        List<ModelNode> subsystemOps = opsBySubsystem.get(subsystem);
                        if (subsystemOps == null) {
                            subsystemOps = new ArrayList<>();
                            opsBySubsystem.put(subsystem, subsystemOps);
                        }
                        subsystemOps.add(op);
                    }
                }

                for (List<ModelNode> ops : opsBySubsystem.values()) {
                    Collections.reverse(ops);
                    for (final ModelNode op : ops) {
                        addOperation(context, op);
                    }
                }
                addOperation(context, profileAdd);
            }
        }, OperationContext.Stage.MODEL, true);

        context.addStep(result, describeOp, handler, OperationContext.Stage.MODEL, true);
    }

    private void addOperation(OperationContext context, ModelNode op) {
        final PathAddress addr = PathAddress.pathAddress(op.require(OP_ADDR));
        final OperationStepHandler h =
                context.getRootResourceRegistration().getOperationHandler(addr, op.get(OP).asString());
        context.addStep(op, h, OperationContext.Stage.MODEL, true);
    }
}
