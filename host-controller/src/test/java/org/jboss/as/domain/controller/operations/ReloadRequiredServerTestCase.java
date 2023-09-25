/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_PORT_OFFSET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.operations.coordination.ServerOperationResolver;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;
import org.jboss.as.host.controller.operations.ServerRestartRequiredServerConfigWriteAttributeHandler;
import org.jboss.as.host.controller.resources.ServerConfigResourceDefinition;
import org.jboss.as.server.operations.ServerProcessStateHandler;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ReloadRequiredServerTestCase extends AbstractOperationTestCase {

    @Test
    public void testChangeServerGroupProfilePrimary() throws Exception {
        testChangeServerGroupProfile(true);
    }

    @Test
    public void testChangeServerGroupProfileSecondary() throws Exception {
        testChangeServerGroupProfile(false);
    }

    private void testChangeServerGroupProfile(boolean primary) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "group-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(PROFILE);
        operation.get(VALUE).set("profile-two");

        try {
            operationContext.executeStep(ServerGroupResourceDefinition.createRestartRequiredHandler(), operation);
        } catch (RuntimeException e) {
            final Throwable t = e.getCause();
            if (t instanceof OperationFailedException) {
                throw (OperationFailedException) t;
            }
            throw e;
        }

        Assert.assertNull(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT));
        checkServerOperationResolver(operationContext, operation, pa, true);
    }

    @Test
    public void testChangeServerGroupProfileNoChangePrimary() throws Exception {
        testChangeServerGroupProfileNoChange(true);
    }

    @Test
    public void testChangeServerGroupProfileNoChangeSecondary() throws Exception {
        testChangeServerGroupProfileNoChange(false);
    }

    private void testChangeServerGroupProfileNoChange(boolean primary) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "group-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(PROFILE);
        operation.get(VALUE).set("profile-one");

        try {
            operationContext.executeStep(ServerGroupResourceDefinition.createRestartRequiredHandler(), operation);
        } catch (RuntimeException e) {
            final Throwable t = e.getCause();
            if (t instanceof OperationFailedException) {
                throw (OperationFailedException) t;
            }
            throw e;
        }

        Assert.assertTrue(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT).contains(operation));
        checkServerOperationResolver(operationContext, operation, pa, false);
    }

//    // WFCORE-833 moved to DomainServerGroupTestCase.testChangeServerGroupInvalidProfile
//    @Test(expected=OperationFailedException.class)
//    public void testChangeServerGroupInvalidProfilePrimary() throws Exception {
//        testChangeServerGroupInvalidProfile(true);
//    }
//
//    // WFCORE-833 moved to DomainServerGroupTestCase.testChangeServerGroupInvalidProfile
//    @Test(expected=OperationFailedException.class)
//    public void testChangeServerGroupInvalidProfileSecondary() throws Exception {
//        testChangeServerGroupInvalidProfile(false);
//    }

    @Override
    AbstractOperationTestCase.MockOperationContext getOperationContext() {
        return super.getOperationContext();
    }

//    // WFCORE-833 moved to DomainServerGroupTestCase.testChangeServerGroupInvalidProfile
//    private void testChangeServerGroupInvalidProfile(boolean primary) throws Exception {
//        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "group-one"));
//        final MockOperationContext operationContext = getOperationContext(false, pa);
//
//        final ModelNode operation = new ModelNode();
//        operation.get(OP_ADDR).set(pa.toModelNode());
//        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
//        operation.get(NAME).set(PROFILE);
//        operation.get(VALUE).set("does-not-exist");
//
//        try {
//            operationContext.executeStep(ServerGroupResourceDefinition.createRestartRequiredHandler(), operation);
//        } catch (RuntimeException e) {
//            final Throwable t = e.getCause();
//            if (t instanceof OperationFailedException) {
//                throw (OperationFailedException) t;
//            }
//            throw e;
//        }
//
//        // WFCORE-833 the rest of this would never have executed because the above would have always thrown the OFE
//        // when the validation handler ran. So really the above test was just a test of the validation and can
//        // be replaced with stuff in core-model-test. It also means the 'primary' param was meaningless
//        operationContext.verify();
//
//        if (!primary) {
//            Assert.assertNull(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT));
//        } else {
//            Assert.fail();
//        }
//    }

    @Test
    public void testChangeServerConfigGroupPrimary() throws Exception {
        testChangeServerConfigGroup(true);
    }

    @Test
    public void testChangeServerConfigGroupSecondary() throws Exception {
        testChangeServerConfigGroup(false);
    }


    public void testChangeServerConfigGroup(boolean primary) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(GROUP);
        operation.get(VALUE).set("group-two");

        ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE.execute(operationContext, operation);
        Assert.assertNull(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT));
        checkServerOperationResolver(operationContext, operation, pa, true);
    }


    @Test
    public void testChangeServerConfigGroupNoChangePrimary() throws Exception {
        testChangeServerConfigGroupNoChange(true);
    }

    @Test
    public void testChangeServerConfigGroupNoChangeSecondary() throws Exception {
        testChangeServerConfigGroupNoChange(true);
    }

    public void testChangeServerConfigGroupNoChange(boolean primary) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(GROUP);
        operation.get(VALUE).set("group-one");

        ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE.execute(operationContext, operation);
        Assert.assertTrue(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT).contains(operation));
        checkServerOperationResolver(operationContext, operation, pa, false);
    }

//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testChangeServerConfigGroupBadGroupPrimary() throws Exception {
//        testChangeServerConfigGroupBadGroup(true);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testChangeServerConfigGroupBadGroupSecondary() throws Exception {
//        testChangeServerConfigGroupBadGroup(false);
//    }

//  // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidServerGroup()
//    private void testChangeServerConfigGroupBadGroup(boolean primary) throws Exception {
//        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
//        final MockOperationContext operationContext = getOperationContext(false, pa);
//
//        final ModelNode operation = new ModelNode();
//        operation.get(OP_ADDR).set(pa.toModelNode());
//        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
//        operation.get(NAME).set(GROUP);
//        operation.get(VALUE).set("bad-group");
//
//        try {
//            operationContext.executeStep(ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE, operation);
//        } catch (RuntimeException e) {
//            final Throwable t = e.getCause();
//            if (t instanceof OperationFailedException) {
//                throw (OperationFailedException) t;
//            }
//            throw e;
//        }
//
//        operationContext.verify();
//
//        if (!primary) {
//            Assert.assertNull(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT));
//        } else {
//            Assert.fail();
//        }
//    }

    @Test
    public void testChangeServerConfigSocketBindingGroupPrimary() throws Exception {
        testChangeServerConfigSocketBindingGroup(true);
    }

    @Test
    public void testChangeServerConfigSocketBindingGroupSecondary() throws Exception {
        testChangeServerConfigSocketBindingGroup(false);
    }

    private void testChangeServerConfigSocketBindingGroup(boolean primary) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(SOCKET_BINDING_GROUP);
        operation.get(VALUE).set("binding-two");

        ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE.execute(operationContext, operation);
        Assert.assertNull(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT));
        checkServerOperationResolver(operationContext, operation, pa, true);
    }


    @Test
    public void testChangeServerConfigSocketBindingGroupNoChangePrimary() throws Exception {
        testChangeServerConfigSocketBindingGroupNoChange(true);
    }

    @Test
    public void testChangeServerConfigSocketBindingGroupNoChangeSecondary() throws Exception {
        testChangeServerConfigSocketBindingGroupNoChange(false);
    }

    private void testChangeServerConfigSocketBindingGroupNoChange(boolean primary) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(SOCKET_BINDING_GROUP);
        operation.get(VALUE).set("binding-one");

        ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE.execute(operationContext, operation);
        Assert.assertTrue(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT).contains(operation));
        checkServerOperationResolver(operationContext, operation, pa, false);

    }

//    // WFCORE-833 moved to DomainServerGroupTestCase.testChangeServerGroupInvalidSocketBindingGroup
//    @Test(expected=OperationFailedException.class)
//    public void testChangeServerConfigSocketBindingGroupBadGroupPrimary() throws Exception {
//        testChangeServerConfigSocketBindingGroupBadGroup(true);
//    }

//    // WFCORE-833 moved to DomainServerGroupTestCase.testChangeServerGroupInvalidSocketBindingGroup
//    @Test(expected=OperationFailedException.class)
//    public void testChangeServerConfigSocketBindingGroupBadGroupSecondary() throws Exception {
//        testChangeServerConfigSocketBindingGroupBadGroup(false);
//    }

//    // WFCORE-833 moved to DomainServerGroupTestCase.testChangeServerGroupInvalidSocketBindingGroup
//    private void testChangeServerConfigSocketBindingGroupBadGroup(boolean primary) throws Exception {
//        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
//        final MockOperationContext operationContext = getOperationContext(false, pa);
//
//        final ModelNode operation = new ModelNode();
//        operation.get(OP_ADDR).set(pa.toModelNode());
//        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
//        operation.get(NAME).set(SOCKET_BINDING_GROUP);
//        operation.get(VALUE).set("bad-group");
//
//        try {
//            operationContext.executeStep(ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE, operation);
//        } catch (RuntimeException e) {
//            final Throwable t = e.getCause();
//            if (t instanceof OperationFailedException) {
//                throw (OperationFailedException) t;
//            }
//            throw e;
//        }
//
//        // WFCORE-833 the rest of this would never have executed because the above would have always thrown the OFE
//        // when the validation handler ran. So really the above test was just a test of the validation and can
//        // be replaced with stuff in core-model-test. It also means the 'primary' param was meaningless
//        operationContext.verify();
//
//        if (!primary) {
//            Assert.assertNull(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT));
//        } else {
//            Assert.fail();
//        }
//    }

    @Test
    public void testChangeServerConfigSocketBindingPortOffset() throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(SOCKET_BINDING_PORT_OFFSET);
        operation.get(VALUE).set(65535);

        ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE.execute(operationContext, operation);
        Assert.assertNull(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT));
        checkServerOperationResolver(operationContext, operation, pa, true);
    }


    @Test
    public void testChangeServerConfigSocketBindingPortOffsetNoChange() throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        operationContext.root.getChild(PathElement.pathElement(HOST, "localhost")).getChild(PathElement.pathElement(SERVER_CONFIG, "server-one")).getModel().get(SOCKET_BINDING_PORT_OFFSET).set(10);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(SOCKET_BINDING_PORT_OFFSET);
        operation.get(VALUE).set(10);

        ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE.execute(operationContext, operation);
        Assert.assertTrue(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT).contains(operation));
        checkServerOperationResolver(operationContext, operation, pa, false);
    }


    @Test
    public void testChangeServerConfigSocketBindingPortNegativeValue() throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        operationContext.root.getChild(PathElement.pathElement(HOST, "localhost")).getChild(PathElement.pathElement(SERVER_CONFIG, "server-one")).getModel().get(SOCKET_BINDING_PORT_OFFSET).set(10);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(SOCKET_BINDING_PORT_OFFSET);
        operation.get(VALUE).set(-65535);

        ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE.execute(operationContext, operation);
        Assert.assertNull(operationContext.getAttachment(ServerOperationResolver.DONT_PROPAGATE_TO_SERVERS_ATTACHMENT));
        checkServerOperationResolver(operationContext, operation, pa, true);
    }

    @Test(expected=OperationFailedException.class)
    public void testChangeServerConfigSocketBindingPortOffsetBadPort() throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = getOperationContext(false, pa);

        operationContext.root.getChild(PathElement.pathElement(HOST, "localhost")).getChild(PathElement.pathElement(SERVER_CONFIG, "server-one")).getModel().get(SOCKET_BINDING_PORT_OFFSET).set(10);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(SOCKET_BINDING_PORT_OFFSET);
        operation.get(VALUE).set(65536);

        ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE.execute(operationContext, operation);
    }

    MockOperationContext getOperationContext(boolean serversOnly, final PathAddress operationAddress) {
        final Resource root = createRootResource();
        return new MockOperationContext(root, false, operationAddress);
    }

    private void checkServerOperationResolver(MockOperationContext context, ModelNode operation, PathAddress address, boolean expectServerOps) {
        Map<String, ProxyController> serverProxies = new HashMap<String, ProxyController>();
        serverProxies.put("server-one", new MockServerProxy());
        serverProxies.put("server-two", new MockServerProxy());
        serverProxies.put("server-three", new MockServerProxy());
        ServerOperationResolver resolver = new ServerOperationResolver("localhost", serverProxies);

        final Resource backup = context.root;
        context.root = getServerResolutionResource();
        try {
            Map<Set<ServerIdentity>, ModelNode> serverOps = resolver.getServerOperations(context, operation, address);
            if (expectServerOps) {
                Assert.assertEquals(1, serverOps.size());
                Set<ServerIdentity> ids = serverOps.entrySet().iterator().next().getKey();
                Assert.assertEquals(1, ids.size());

                ServerIdentity expected = new ServerIdentity("localhost", "group-one","server-one");
                assertEquals(expected, ids.iterator().next());

                ModelNode expectedOp = new ModelNode();

                expectedOp.get(OP).set(ServerProcessStateHandler.REQUIRE_RELOAD_OPERATION);
                expectedOp.get(OP_ADDR).setEmptyList();
                Assert.assertEquals(expectedOp, serverOps.get(ids));
            } else {
                Assert.assertEquals(0, serverOps.size());
            }
        } finally {
            context.root = backup;
        }
    }

    private Resource getServerResolutionResource() {

        final Resource result = Resource.Factory.create();
        final Resource host =  Resource.Factory.create();
        result.registerChild(PathElement.pathElement(HOST, "localhost"), host);
        final Resource serverOne = Resource.Factory.create();
        serverOne.getModel().get(GROUP).set("group-one");
        serverOne.getModel().get(SOCKET_BINDING_GROUP).set("group-one");
        host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server-one"), serverOne);
        final Resource serverTwo = Resource.Factory.create();
        serverTwo.getModel().get(GROUP).set("nope");
        host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server-two"), serverTwo);
        final Resource serverThree = Resource.Factory.create();
        serverThree.getModel().get(GROUP).set("nope");
        host.registerChild(PathElement.pathElement(SERVER_CONFIG, "server-three"), serverThree);

        return result;
    }

    private class MockServerProxy implements ProxyController {

        @Override
        public PathAddress getProxyNodeAddress() {
            return null;
        }

        @Override
        public void execute(ModelNode operation, OperationMessageHandler handler, ProxyOperationControl control,
                            OperationAttachments attachments, BlockingTimeout blockingTimeout) {
        }

    }

    private class MockOperationContext extends AbstractOperationTestCase.MockOperationContext {
        private boolean reloadRequired;
        private OperationStepHandler nextStep;
        protected MockOperationContext(final Resource root, final boolean booting, final PathAddress operationAddress) {
            super(root, booting, operationAddress);
            Set<RuntimeCapability> capabilities = new HashSet<>();
            capabilities.add(ServerConfigResourceDefinition.SERVER_CONFIG_CAPABILITY);
            capabilities.add(ServerGroupResourceDefinition.SERVER_GROUP_CAPABILITY);
            when(this.registration.getCapabilities()).thenReturn(capabilities);
        }

        void executeStep(OperationStepHandler handler, ModelNode operation) throws OperationFailedException {
            handler.execute(this, operation);
            completed();
        }

        public void completeStep(ResultHandler resultHandler) {
            if (nextStep != null) {
                completed();
            } else {
                resultHandler.handleResult(ResultAction.KEEP, this, null);
            }
        }

        private void completed() {
            if (nextStep != null) {
                try {
                    OperationStepHandler step = nextStep;
                    nextStep = null;
                    step.execute(this, null);
                } catch (OperationFailedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void addStep(OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            nextStep = step;
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
    }
}
