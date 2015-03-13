/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat Middleware LLC, and individual contributors
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Test to verify that calling revertReloadRequired does not generate a NPE if
 * reloadRequired was not called first.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2015 Red Hat inc.
 */
public class RevertReloadRequiredTestCase extends AbstractControllerTestBase {

    private static ModelNode createdResource = null;
    private static Throwable throwable = null;

    @Test
    public void testRevertReloadRequiredWithoutReloadRequired() throws Exception {
        assertThat(createdResource, is(nullValue()));

        //Just make sure it works as expected for an existent resource
        ModelNode op = Util.createAddOperation(PathAddress.pathAddress("test", "exists"));
        executeCheckForFailure(op);

        assertThat(createdResource, is(nullValue()));
        // call to context.revertReloadRequired() must not throw a NPE
        assertThat(throwable, is(nullValue()));
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);
        GlobalNotifications.registerGlobalNotifications(registration, processType);
        registration.registerSubModel(new TestResource());
    }

    private static class TestResource extends SimpleResourceDefinition {

        public TestResource() {
            super(PathElement.pathElement("test"),
                    new NonResolvingResourceDescriptionResolver(),
                    new TestResourceAddHandler(),
                    new AbstractRemoveStepHandler() {
                    });
        }
    }

    private static class TestResourceAddHandler extends AbstractAddStepHandler {

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            context.setRollbackOnly();
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            try {
                context.revertReloadRequired();
            } catch (Throwable t) {
                throwable = t;
            }
        }
    }
}
