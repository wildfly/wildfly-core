/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerGroupAffectedResourceServerGroupOperationsTestCase extends AbstractOperationTestCase {

    @Test
    public void testAddServerGroupPrimary() throws Exception {
        testAddServerGroup(true, false);
    }

    @Test
    public void testAddServerGroupSecondary() throws Exception {
        testAddServerGroup(false, false);
    }

    @Test
    public void testAddServerGroupPrimaryRollback() throws Exception {
        testAddServerGroup(true, true);
    }

    @Test
    public void testAddServerGroupSecondaryRollback() throws Exception {
        testAddServerGroup(false, true);
    }

    private void testAddServerGroup(boolean primary, boolean rollback) throws Exception {
        testAddServerGroupBadInfo(primary, rollback, false, false);
    }

//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testAddServerGroupBadProfile()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerGroupBadProfilePrimary() throws Exception {
//        testAddServerGroupBadInfo(true, false, true, false);
//    }
//
//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testAddServerGroupBadProfile()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerGroupBadProfileSecondary() throws Exception {
//        testAddServerGroupBadInfo(false, false, true, false);
//    }
//
//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testAddServerGroupBadProfile()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerGroupBadProfilePrimaryRollback() throws Exception {
//        //This won't actually get to the rollback part
//        testAddServerGroupBadInfo(true, true, true, false);
//    }
//
//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testAddServerGroupBadProfile()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerGroupBadProfileSecondaryRollback() throws Exception {
//        testAddServerGroupBadInfo(false, true, true, false);
//    }

      // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testAddServerGroupBadSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerGroupBadSocketBindingGroupPrimary() throws Exception {
//        testAddServerGroupBadInfo(true, false, false, true);
//    }

    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testAddServerGroupBadSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerGroupBadSocketBindingGroupSecondary() throws Exception {
//        testAddServerGroupBadInfo(false, false, false, true);
//    }

    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testAddServerGroupBadSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerGroupBadSocketBindingGroupPrimaryRollback() throws Exception {
//        //This won't actually get to the rollback part
//        testAddServerGroupBadInfo(true, true, false, true);
//    }

    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testAddServerGroupBadSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerGroupBadSocketBindingGroupSecondaryRollback() throws Exception {
//        testAddServerGroupBadInfo(false, true, false, true);
//    }

    private void testAddServerGroupBadInfo(boolean primary, boolean rollback, boolean badProfile, boolean badSocketBindingGroup) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "group-three"));
        final MockOperationContext operationContext = getOperationContext(rollback, pa);

        String profileName = badProfile ? "bad-profile" : "profile-two";
        String socketBindingGroupName = badSocketBindingGroup ? "bad-group" : "binding-two";

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(ADD);
        operation.get(PROFILE).set(profileName);
        operation.get(SOCKET_BINDING_GROUP).set(socketBindingGroupName);

        try {
            operationContext.executeStep(ServerGroupAddHandler.INSTANCE, operation);
        } catch(RuntimeException e) {
            // MockOperationContext impl throws RuntimeException in case one of the steps
            // throws OperationFailedException
            final Throwable t = e.getCause();
            if(t instanceof OperationFailedException) {
                throw (OperationFailedException) t;
            }
            throw e;
        }

        if (primary && (badProfile || badSocketBindingGroup)) {
            // Assert.fail();
        }

        if (rollback) {
            Assert.assertFalse(operationContext.isReloadRequired());
        }
    }

    @Test
    public void testRemoveServerGroupPrimary() throws Exception {
        testRemoveServerGroup(true, false);
    }

    @Test
    public void testRemoveServerGroupSecondary() throws Exception {
        testRemoveServerGroup(false, false);
    }

    @Test
    public void testRemoveServerGroupPrimaryRollback() throws Exception {
        testRemoveServerGroup(true, true);
    }

    @Test
    public void testRemoveServerGroupSecondaryRollback() throws Exception {
        testRemoveServerGroup(false, true);
    }

    private void testRemoveServerGroup(boolean primary, boolean rollback) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "group-one"));
        final MockOperationContext operationContext = getOperationContext(rollback, pa);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(REMOVE);

        new ServerGroupRemoveHandler(null).execute(operationContext, operation);

        Assert.assertFalse(operationContext.isReloadRequired());
    }

    @Test
    public void testUpdateServerGroupProfilePrimary() throws Exception {
        testUpdateServerGroupProfile(true, false, false);
    }

    @Test
    public void testUpdateServerGroupProfileSecondary() throws Exception {
        testUpdateServerGroupProfile(false, false, false);

    }

    @Test
    public void testUpdateServerGroupProfilePrimaryRollback() throws Exception {
        testUpdateServerGroupProfile(true, true, false);
    }

    @Test
    public void testUpdateServerGroupProfileSecondaryRollback() throws Exception {
        testUpdateServerGroupProfile(false, true, false);
    }

//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testChangeServerGroupInvalidProfile()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerGroupBadProfilePrimary() throws Exception {
//        testUpdateServerGroupProfile(true, false, true);
//    }
//
//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testChangeServerGroupInvalidProfile()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerGroupBadProfileSecondary() throws Exception {
//        testUpdateServerGroupProfile(false, false, true);
//    }
//
//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testChangeServerGroupInvalidProfile()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerGroupBadProfilePrimaryRollback() throws Exception {
//        testUpdateServerGroupProfile(true, true, true);
//    }
//
//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testChangeServerGroupInvalidProfile()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerGroupBadProfileSecondaryRollback() throws Exception {
//        testUpdateServerGroupProfile(false, true, true);
//    }

    private void testUpdateServerGroupProfile(boolean primary, boolean rollback, boolean badProfile) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "group-one"));
        final MockOperationContext operationContext = getOperationContext(rollback, pa);

        String profileName = badProfile ? "bad-profile" : "profile-two";

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(PROFILE);
        operation.get(VALUE).set(profileName);

        try {
            operationContext.executeStep(ServerGroupResourceDefinition.createRestartRequiredHandler(), operation);
        } catch (RuntimeException e) {
            final Throwable t = e.getCause();
            if (t instanceof OperationFailedException) {
                throw (OperationFailedException) t;
            }
            throw e;
        }

        if (primary && badProfile) {
            //primary will throw an exception
            Assert.fail();
        }

        if (rollback) {
            Assert.assertFalse(operationContext.isReloadRequired());
        }
    }

    @Test
    public void testUpdateServerGroupSocketBindingGroupPrimary() throws Exception {
        testUpdateServerGroupSocketBindingGroup(true, false, false);
    }

    @Test
    public void testUpdateServerGroupSocketBindingGroupSecondary() throws Exception {
        testUpdateServerGroupSocketBindingGroup(false, false, false);

    }

    @Test
    public void testUpdateServerGroupSocketBindingGroupPrimaryRollback() throws Exception {
        testUpdateServerGroupSocketBindingGroup(true, true, false);

    }

    @Test
    public void testUpdateServerGroupSocketBindingGroupSecondaryRollback() throws Exception {
        testUpdateServerGroupSocketBindingGroup(false, true, false);
    }

//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testChangeServerGroupInvalidSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerGroupBadSocketBindingGroupPrimary() throws Exception {
//        testUpdateServerGroupSocketBindingGroup(true, false, true);
//    }
//
//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testChangeServerGroupInvalidSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerGroupBadSocketBindingGroupSecondary() throws Exception {
//        testUpdateServerGroupSocketBindingGroup(false, false, true);
//    }
//
//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testChangeServerGroupInvalidSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerGroupBadSocketBindingGroupPrimaryRollback() throws Exception {
//        testUpdateServerGroupSocketBindingGroup(true, true, true);
//    }
//
//    // WFCORE-833 replaced by core-model-test DomainServerGroupTestCase.testChangeServerGroupInvalidSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerGroupBadSocketBindingGroupSecondaryRollback() throws Exception {
//        testUpdateServerGroupSocketBindingGroup(false, true, true);
//    }

    private void testUpdateServerGroupSocketBindingGroup(boolean primary, boolean rollback, boolean badSocketBindingGroup) throws Exception {

        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "group-one"));
        final MockOperationContext operationContext = getOperationContext(rollback, pa);

        String socketBindingGroupName = badSocketBindingGroup ? "bad-group" : "binding-two";

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(SOCKET_BINDING_GROUP);
        operation.get(VALUE).set(socketBindingGroupName);

        try {
            operationContext.executeStep(ServerGroupResourceDefinition.createRestartRequiredHandler(), operation);
        } catch (RuntimeException e) {
            final Throwable t = e.getCause();
            if (t instanceof OperationFailedException) {
                throw (OperationFailedException) t;
            }
            throw e;
        }

        if (primary && badSocketBindingGroup) {
            //primary will throw an exception
            Assert.fail();
        }

        if (rollback) {
            Assert.assertFalse(operationContext.isReloadRequired());
        }
    }

    MockOperationContext getOperationContext(final boolean rollback, final PathAddress operationAddress) {
        final Resource root = createRootResource();
        return new MockOperationContext(root, false, operationAddress, rollback);
    }

    private class MockOperationContext extends AbstractOperationTestCase.MockOperationContext {
        private boolean reloadRequired;
        private boolean rollback;
        private OperationStepHandler nextStep;
        private ModelNode nextStepOp;

        protected MockOperationContext(final Resource root, final boolean booting, final PathAddress operationAddress, final boolean rollback) {
            super(root, booting, operationAddress);
            this.rollback = rollback;
            when(this.registration.getCapabilities()).thenReturn(Collections.singleton(ServerGroupResourceDefinition.SERVER_GROUP_CAPABILITY));
        }

        void executeStep(OperationStepHandler handler, ModelNode operation) throws OperationFailedException {
            handler.execute(this, operation);
            if (nextStep != null) {
                completed();
            }
        }

        @Override
        public void completeStep(ResultHandler resultHandler) {
            if (nextStep != null) {
                completed();
            } else if (rollback) {
                resultHandler.handleResult(ResultAction.ROLLBACK, this, null);
            }
        }

        @Override
        public void stepCompleted() {
            completed();
        }

        private void completed() {
            if (nextStep != null) {
                try {
                    OperationStepHandler step = nextStep;
                    nextStep = null;
                    step.execute(this, nextStepOp);
                } catch (OperationFailedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void reloadRequired() {
            reloadRequired = true;
        }

        public boolean isReloadRequired() {
            return reloadRequired;
        }

        public void revertReloadRequired() {
            reloadRequired = false;
        }

        public void addStep(OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            addStep(null, step, stage);
        }

        public void addStep(ModelNode operation, OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            nextStep = step;
            nextStepOp = operation;
        }
    }
}
