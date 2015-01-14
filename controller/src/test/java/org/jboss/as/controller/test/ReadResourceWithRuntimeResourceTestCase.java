/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

/**
 * Test to verify the behaviour of undefined runtime resource.
 *
 * The :read-resource(include-runtime=true) operation will return
 * any registered runtime resource (and read their attributes) even though
 * there is no actual resource at their address.
 *
 * @see org.jboss.as.controller.operations.global.ReadResourceHandler
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2014 Red Hat inc.
 */
public class ReadResourceWithRuntimeResourceTestCase extends AbstractControllerTestBase {

    @Test
    public void testReadResourceWithNoRuntimeResource() throws Exception {
        ModelNode operation = createOperation(READ_RESOURCE_OPERATION, "subsystem", "mysubsystem");
        // use a recursive operations as we are interested by the /susbsystem=mysubsystem's children
        operation.get(RECURSIVE).set(true);

        // read-resource(include-runtime=false) must not return the resource=B child
        operation.get(INCLUDE_RUNTIME).set(false);
        ModelNode result = executeForResult(operation);
        assertEquals(1, result.keys().size());
        ModelNode children = result.get("resource");
        assertEquals(1, children.keys().size());
        assertTrue(children.keys().contains("A"));
        assertFalse(children.keys().contains("B"));

        // read-resource(include-runtime=true) returns the resource=B child (and its attribute)
        // even though it not defined in the model
        operation.get(INCLUDE_RUNTIME).set(true);
        result = executeForResult(operation);
        assertEquals(1, result.keys().size());
        children = result.get("resource");
        assertEquals(2, children.keys().size());
        assertTrue(children.keys().contains("A"));
        assertTrue(children.keys().contains("B"));
        ModelNode resourceB = children.get("B");
        // the operation has called the handler for the attribute even though
        // there is no resource=B in the model.
        assertEquals(-1, resourceB.get("attr").asLong());
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);
        GlobalNotifications.registerGlobalNotifications(registration, processType);

        ManagementResourceRegistration subsystemRegistration = registration.registerSubModel(
                new SimpleResourceDefinition(PathElement.pathElement("subsystem", "mysubsystem"), new NonResolvingResourceDescriptionResolver()));
        // /subsystem=mysubsystem/resource=A is a regular resource
        subsystemRegistration.registerSubModel(
                new SimpleResourceDefinition(PathElement.pathElement("resource", "A"), new NonResolvingResourceDescriptionResolver()));
        // /subsystem=mysubsystem/resource=B is a runtime-only resource
        ManagementResourceRegistration runtimeResource = subsystemRegistration.registerSubModel(
                new SimpleResourceDefinition(PathElement.pathElement("resource", "B"), new NonResolvingResourceDescriptionResolver()));
        AttributeDefinition runtimeAttr = TestUtils.createAttribute("attr", ModelType.LONG);
        runtimeResource.registerReadOnlyAttribute(runtimeAttr, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.getResult().set(-1);
            }
        });
        runtimeResource.setRuntimeOnly(true);

        registration.registerOperationHandler(TestUtils.SETUP_OPERATION_DEF, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ModelNode model = new ModelNode();

                // create a model with the resource=A child
                // but *no* runtime resource=B child
                model.get("subsystem", "mysubsystem", "resource", "A").setEmptyObject();

                createModel(context, model);
            }
        });
    }
}
