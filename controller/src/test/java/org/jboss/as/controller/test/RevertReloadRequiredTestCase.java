/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

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
                    NonResolvingResourceDescriptionResolver.INSTANCE,
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
