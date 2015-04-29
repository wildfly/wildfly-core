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

package org.jboss.as.test.integration.domain.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Fake extension to use in testing extension management.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 * @author Kabir Khan
 */
public class TestHostCapableExtension implements Extension {

    public static final String MODULE_NAME = "org.jboss.as.test.hc.extension";
    public static final String SUBSYSTEM_NAME = "HC";

    private final EmptySubsystemParser parser = new EmptySubsystemParser("urn:jboss:test:extension:HC:1.0");


    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration reg = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1, 1, 1));
        reg.setHostCapable();
        reg.registerXMLElementWriter(parser);
        reg.registerSubsystemModel(new RootResourceDefinition(SUBSYSTEM_NAME));
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, parser.getNamespace(), parser);
    }

    private static class RootResourceDefinition extends SimpleResourceDefinition {

        private static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder("name", ModelType.STRING, false).build();
        private static final OperationDefinition TEST_OP = new SimpleOperationDefinitionBuilder("test-op", new NonResolvingResourceDescriptionResolver()).build();

        public RootResourceDefinition(String name) {
            super(PathElement.pathElement(SUBSYSTEM, name), new NonResolvingResourceDescriptionResolver(),
                    new AddSubsystemHandler(), new RemoveSubsystemHandler());
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);
            resourceRegistration.registerReadWriteAttribute(NAME, null, new ReloadRequiredWriteAttributeHandler(NAME));
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
            resourceRegistration.registerOperationHandler(TEST_OP, new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    ServiceController<?> sc = context.getServiceRegistry(false).getService(createServiceName(context.getCurrentAddress()));
                    context.getResult().set(sc != null);
                }
            });
        }

        private static ServiceName createServiceName(PathAddress address) {
            ServiceName name = ServiceName.JBOSS;
            name = name.append("test");
            for (PathElement element : address) {
                name = name.append(element.getKey(), element.getValue());
            }
            return name;
        }

        private static class AddSubsystemHandler extends AbstractAddStepHandler {

            @Override
            protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
                NAME.validateAndSet(operation, model);
            }

            @Override
            protected void performRuntime(OperationContext context, ModelNode operation, Resource resource)
                    throws OperationFailedException {
                context.getServiceTarget().addService(createServiceName(context.getCurrentAddress()), new TestService()).install();
            }
        }


        private static class RemoveSubsystemHandler extends AbstractRemoveStepHandler {
            @Override
            protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                    throws OperationFailedException {
                context.removeService(createServiceName(context.getCurrentAddress()));
            }
        }

        private static class TestService implements Service<TestService> {

            @Override
            public TestService getValue() throws IllegalStateException, IllegalArgumentException {
                return this;
            }

            @Override
            public void start(StartContext context) throws StartException {
            }

            @Override
            public void stop(StopContext context) {
            }

        }
    }

}
