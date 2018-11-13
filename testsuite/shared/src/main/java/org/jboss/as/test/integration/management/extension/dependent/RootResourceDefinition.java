/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2014, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */

package org.jboss.as.test.integration.management.extension.dependent;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Collection;
import java.util.Collections;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * @author Brian Stansberry (c) 2016 Red Hat Inc.
 */
public class RootResourceDefinition extends PersistentResourceDefinition {
    private static final String RUNTIME_CAPABILITY_NAME = "org.wildfly.test.dependent";

    private static final RuntimeCapability<Void> RUNTIME_CAPABILITY =
            RuntimeCapability.Builder.of(RUNTIME_CAPABILITY_NAME, false, Integer.class).build();

    static final SimpleAttributeDefinition ATTRIBUTE = new SimpleAttributeDefinitionBuilder("osb", ModelType.STRING)
            .setCapabilityReference("org.wildfly.network.outbound-socket-binding", RUNTIME_CAPABILITY)
            .build();

    static final PersistentResourceDefinition INSTANCE = new RootResourceDefinition();

    private RootResourceDefinition() {
        super(new SimpleResourceDefinition.Parameters(PathElement.pathElement(SUBSYSTEM, DependentExtension.SUBSYSTEM_NAME), new NonResolvingResourceDescriptionResolver())
                .setAddHandler(AddSubsystemHandler.INSTANCE)
                .setRemoveHandler(new ServiceRemoveStepHandler(AddSubsystemHandler.INSTANCE, RUNTIME_CAPABILITY))
                .setCapabilities(RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler write = new AbstractWriteAttributeHandler<Void>(ATTRIBUTE) {

            @Override
            protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
                // no-op
                return false;
            }

            @Override
            protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
                // no-op
            }
        };
        resourceRegistration.registerReadWriteAttribute(ATTRIBUTE, null, write);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.singleton(ATTRIBUTE);
    }

    private static class AddSubsystemHandler extends AbstractAddStepHandler {

        private static final AbstractAddStepHandler INSTANCE = new AddSubsystemHandler();

        private AddSubsystemHandler()  {
            super(ATTRIBUTE);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            String osb = ATTRIBUTE.resolveModelAttribute(context, resource.getModel()).asString();
            Service<Void> service = new AbstractService<Void>() {};
            ServiceBuilder sb = context.getServiceTarget().addService(ServiceName.of("wfcore-1106"), service);
            sb.requires(context.getCapabilityServiceName("org.wildfly.network.outbound-socket-binding", osb, OutboundSocketBinding.class));
            sb.install();
        }
    }
}
