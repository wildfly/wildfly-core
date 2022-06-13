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
package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LEVEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_REQUIRES_RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_REQUIRES_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROCESS_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_TYPES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLED_BACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_MODIFICATION_BEGUN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_MODIFICATION_COMPLETE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_UPDATE_SKIPPED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WARNING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WARNINGS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.test.TestUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link ModelControllerImpl}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
public class ModelControllerImplUnitTestCase {

    private static final PathAddress CHILD_ONE = PathAddress.pathAddress(PathElement.pathElement("child", "one"));
    private static final PathAddress CHILD_TWO = PathAddress.pathAddress(PathElement.pathElement("child", "two"));
    private ServiceContainer container;
    private ModelController controller;
    private AtomicBoolean sharedState;
    private ServiceNotificationHandler notificationHandler;

    public static void toggleRuntimeState(AtomicBoolean state) {
        boolean runtimeVal = false;
        while (!state.compareAndSet(runtimeVal, !runtimeVal)) {
            runtimeVal = !runtimeVal;
        }
    }

    @Before
    public void setupController() throws InterruptedException {

        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        ModelControllerService svc = new ModelControllerService();
        target.addService(ServiceName.of("ModelController")).setInstance(svc).install();
        sharedState = svc.getSharedState();
        svc.awaitStartup(30, TimeUnit.SECONDS);
        controller = svc.getValue();
        ModelNode setup = Util.getEmptyOperation("setup", new ModelNode());
        controller.execute(setup, null, null, null);
        notificationHandler = new ServiceNotificationHandler();
        controller.getNotificationRegistry().registerNotificationHandler(PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT).append(SERVICE, MANAGEMENT_OPERATIONS), notificationHandler, notificationHandler);
        assertEquals(ControlledProcessState.State.RUNNING, svc.getCurrentProcessState());
    }

    @After
    public void shutdownServiceContainer() {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                container = null;
            }
        }
    }

    @Test
    public void testGoodModelExecution() throws Exception {
        ModelNode result = controller.execute(getOperation("good", "attr1", 5), null, null, null);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(0);
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertEquals(5, result.get(RESULT).asInt());
        notificationHandler.validate(0);
    }

    /**
     * Test successfully updating the model but then having the caller roll back the transaction.
     */
    @Test
    public void testGoodModelExecutionTxRollback() {
        ModelNode result = controller.execute(getOperation("good", "attr1", 5), null, RollbackTransactionControl.INSTANCE, null);
        // Store response data for later assertions after we check more critical stuff
        String outcome = result.get(OUTCOME).asString();
        boolean rolledback = result.get(ROLLED_BACK).asBoolean();
        notificationHandler.validate(0);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertEquals(1, result.get(RESULT).asInt());

        // Assert the first response was as expected
        assertEquals(FAILED, outcome);  // TODO success may be valid???
        assertTrue(rolledback);
        notificationHandler.validate(0);
    }

    @Test
    public void testModelStageFailureExecution() throws Exception {
        ModelNode result = controller.execute(getOperation("bad", "attr1", 5), null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertEquals("this request is bad", result.get(FAILURE_DESCRIPTION).asString());
        notificationHandler.validate(0);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertEquals(1, result.get(RESULT).asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testModelStageUnhandledFailureExecution() throws Exception {
        ModelNode result = controller.execute(getOperation("evil", "attr1", 5), null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.get(FAILURE_DESCRIPTION).toString().contains("this handler is evil"));

        // Confirm runtime state was unchanged
        assertTrue(sharedState.get());
        notificationHandler.validate(0);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testHandleFailedExecution() throws Exception {
        ModelNode result = controller.execute(getOperation("handleFailed", "attr1", 5, "good", false), null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertEquals("handleFailed", result.get("failure-description").asString());

        // Confirm runtime state was unchanged
        assertTrue(sharedState.get());
        notificationHandler.validate(0);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testRuntimeStageFailedNoRollback() throws Exception {

        ModelNode op = getOperation("handleFailed", "attr1", 5, "good");
        op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
        ModelNode result = controller.execute(op, null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertEquals("handleFailed", result.get("failure-description").asString());

        // Confirm runtime state was changed
        assertFalse(sharedState.get());
        notificationHandler.validate(0);

        // Confirm model was changed
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(5, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testRuntimeStageUnhandledFailureNoRollback() throws Exception {

        ModelNode op = getOperation("runtimeException", "attr1", 5, "good");
        op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
        ModelNode result = controller.execute(op, null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.get("failure-description").toString().contains("runtime exception"));

        // Confirm runtime state was changed (handler changes it and throws exception, does not fix state)
        assertFalse(sharedState.get());
        notificationHandler.validate(0);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testOperationFailedExceptionNoRollback() throws Exception {

        ModelNode op = getOperation("operationFailedException", "attr1", 5, "good");
        op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
        ModelNode result = controller.execute(op, null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.get("failure-description").toString().contains("OFE"));
        notificationHandler.validate(0);

        // Confirm model was changed
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        System.out.println(result);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(5, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testPathologicalRollback() throws Exception {
        ModelNode result = controller.execute(getOperation("bad", "attr1", 5), null, null, null); // don't tell it to call the 'good' op on rollback
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertEquals("this request is bad", result.get("failure-description").asString());

        // Confirm runtime state was unchanged
        assertTrue(sharedState.get());
        notificationHandler.validate(0);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testGoodService() throws Exception {
        ModelNode result = controller.execute(getOperation("good-service", "attr1", 5), null, null, null);
        System.out.println(result);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());

        ServiceController<?> sc = container.getService(ServiceName.JBOSS.append("good-service"));
        assertNotNull(sc);
        assertEquals(ServiceController.State.UP, sc.getState());
        notificationHandler.validate(1);

        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(5, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testGoodServiceTxRollback() throws Exception {
        ModelNode result = controller.execute(getOperation("good-service", "attr1", 5), null, RollbackTransactionControl.INSTANCE, null);
        // Store response data for later assertions after we check more critical stuff

        ServiceController<?> sc = container.getService(ServiceName.JBOSS.append("good-service"));
        if (sc != null) {
            assertEquals(ServiceController.Mode.REMOVE, sc.getMode());
        }
        notificationHandler.validate(2);

        // Confirm model was unchanged
        ModelNode result2 = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals(SUCCESS, result2.get(OUTCOME).asString());
        assertEquals(1, result2.get(RESULT).asInt());

        // Assert the first response was as expected
        assertEquals(FAILED, result.get(OUTCOME).asString());   // TODO success may be valid???
        assertTrue(result.get(ROLLED_BACK).asBoolean());
        notificationHandler.validate(0);
    }

    @Test
    public void testGoodServiceInvalidUpdateRollback() throws Exception {
        ModelNode operation=getOperation("good-service", "attr1", 5);
        ModelNode result = controller.execute(operation, null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());

        ServiceController<?> sc = container.getService(ServiceName.JBOSS.append("good-service"));
        assertNotNull(sc);
        assertEquals(ServiceController.State.UP, sc.getState());
        notificationHandler.validate(1);

        ModelNode result2 = controller.execute(getOperation("invalid-service-update", "attr1", 5), null, null, null);
        assertEquals("failed", result2.get("outcome").asString());
        notificationHandler.validate(2);

        ServiceController<?> sc2 = container.getService(ServiceName.JBOSS.append("good-service"));
        assertNotNull(sc2);
        assertEquals(ServiceController.State.UP, sc2.getState());
    }

    @Test
    public void testBadService() throws Exception {
        ModelNode result = controller.execute(getOperation("bad-service", "attr1", 5, "good"), null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(FAILURE_DESCRIPTION));

        ServiceController<?> sc = container.getService(ServiceName.JBOSS.append("bad-service"));
        if (sc != null) {
            assertEquals(ServiceController.Mode.REMOVE, sc.getMode());
        }
        notificationHandler.validate(2);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testMissingService() throws Exception {
        ModelNode result = controller.execute(getOperation("missing-service", "attr1", 5, "good"), null, null, null);
        System.out.println(result);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(FAILURE_DESCRIPTION));

        ServiceController<?> sc = container.getService(ServiceName.JBOSS.append("missing-service"));
        if (sc != null) {
            assertEquals(ServiceController.Mode.REMOVE, sc.getMode());
        }
        notificationHandler.validate(2);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testGlobal() throws Exception {

        ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(RECURSIVE).set(false);

        ModelNode result = controller.execute(operation, null, null, null);
        assertTrue(result.get("result").hasDefined("child"));
        assertTrue(result.get("result", "child").has("one"));
        assertFalse(result.get("result", "child").hasDefined("one"));
        notificationHandler.validate(0);

        operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(RECURSIVE).set(true);

        result = controller.execute(operation, null, null, null);
        assertTrue(result.get("result", "child", "one").hasDefined("attribute1"));
        assertEquals(1, result.get("result", "child", "one","attribute1").asInt());
        assertFalse(result.get("result", "child", "one").has("attribute2"));
        assertFalse(result.get("result", "child", "two").has("attribute2"));
        notificationHandler.validate(0);

        operation.get(INCLUDE_RUNTIME).set(true);
        result = controller.execute(operation, null, null, null);
        assertTrue(result.get("result", "child", "one").hasDefined("attribute1"));
        assertEquals(1, result.get("result", "child", "one","attribute1").asInt());
        assertTrue(result.get("result", "child", "one").has("attribute2"));
        assertFalse(result.get("result", "child", "one").hasDefined("attribute2"));
        assertEquals(result.toString(), 2, result.get("result", "child", "two","attribute2").asInt());
        notificationHandler.validate(0);

        operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(CHILD_TYPE).set("child");

        result = controller.execute(operation, null, null, null).get("result");
        assertEquals("one", result.get(0).asString());
        assertEquals("two", result.get(1).asString());
        notificationHandler.validate(0);

        operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(CHILD_TYPE).set("runtime-child");

        result = controller.execute(operation, null, null, null).get("result");
        assertTrue(result.asList().isEmpty());
        notificationHandler.validate(0);

        operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(CHILD_TYPE).set("deployment");

        result = controller.execute(operation, null, null, null).get("result");
        assertEquals("runtime", result.get(0).asString());
        notificationHandler.validate(0);

        operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_TYPES_OPERATION);
        operation.get(OP_ADDR).setEmptyList();

        result = controller.execute(operation, null, null, null).get("result");
        assertEquals("child", result.get(0).asString());
        assertEquals("deployment", result.get(1).asString());
        assertEquals("runtime-child", result.get(2).asString());
        notificationHandler.validate(0);

        operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(CHILD_TYPE).set("child");

        result = controller.execute(operation, null, null, null).get("result");
        assertEquals(result.toString(), 2, result.asInt());
        Iterator<String> iter = result.keys().iterator();
        assertEquals(result.toString(), "one", iter.next());
        assertEquals(result.toString(), "two", iter.next());

        notificationHandler.validate(0);
    }

    @Test
    public void testReloadRequired() throws Exception {
        ModelNode result = controller.execute(getOperation("reload-required", "attr1", 5), null, null, null);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.get(RESPONSE_HEADERS, RUNTIME_UPDATE_SKIPPED).asBoolean());
        assertTrue(result.get(RESPONSE_HEADERS, OPERATION_REQUIRES_RELOAD).asBoolean());
        assertEquals(ControlledProcessState.State.RELOAD_REQUIRED.toString(), result.get(RESPONSE_HEADERS, PROCESS_STATE).asString());
        notificationHandler.validate(0);

        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(5, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testRestartRequired() throws Exception {
        ModelNode result = controller.execute(getOperation("restart-required", "attr1", 5), null, null, null);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.get(RESPONSE_HEADERS, RUNTIME_UPDATE_SKIPPED).asBoolean());
        assertTrue(result.get(RESPONSE_HEADERS, OPERATION_REQUIRES_RESTART).asBoolean());
        assertEquals(ControlledProcessState.State.RESTART_REQUIRED.toString(), result.get(RESPONSE_HEADERS, PROCESS_STATE).asString());
        notificationHandler.validate(0);

        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(5, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testReloadRequiredTxRollback() throws Exception {
        ModelNode result = controller.execute(getOperation("reload-required", "attr1", 5), null, RollbackTransactionControl.INSTANCE, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.get(RESPONSE_HEADERS, RUNTIME_UPDATE_SKIPPED).asBoolean());
        assertFalse(result.get(RESPONSE_HEADERS).hasDefined(OPERATION_REQUIRES_RELOAD));
        assertFalse(result.get(RESPONSE_HEADERS).hasDefined(PROCESS_STATE));
        notificationHandler.validate(0);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testRestartRequiredTxRollback() throws Exception {
        ModelNode result = controller.execute(getOperation("restart-required", "attr1", 5), null, RollbackTransactionControl.INSTANCE, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.get(RESPONSE_HEADERS, RUNTIME_UPDATE_SKIPPED).asBoolean());
        assertFalse(result.get(RESPONSE_HEADERS).hasDefined(OPERATION_REQUIRES_RESTART));
        assertFalse(result.get(RESPONSE_HEADERS).hasDefined(PROCESS_STATE));
        notificationHandler.validate(0);

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testRemoveDependentService() throws Exception {
        ModelNode result = controller.execute(getOperation("dependent-service", "attr1", 5), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(1, result.get("result").asInt());
        notificationHandler.validate(1);

        ServiceController<?> sc = container.getService(ServiceName.JBOSS.append("depended-service"));
        assertNotNull(sc);
        assertEquals(ServiceController.State.UP, sc.getState());

        sc = container.getService(ServiceName.JBOSS.append("dependent-service"));
        assertNotNull(sc);
        assertEquals(ServiceController.State.UP, sc.getState());

        result = controller.execute(getOperation("remove-dependent-service", "attr1", 6, "good"), null, null, null);
        sc = container.getService(ServiceName.JBOSS.append("depended-service"));
        boolean outcome = FAILED.equals(result.get(OUTCOME).asString());
        if (!outcome) {
            if (sc == null) {
                System.out.println("Null depended service!");
            } else {
                System.out.println(sc.getName());
                System.out.println("Mode = " + sc.getMode());
                System.out.println("State = " + sc.getState());
            }

            sc = container.getService(ServiceName.JBOSS.append("dependent-service"));
            if (sc == null) {
                System.out.println("Null dependent service!");
            } else {
                System.out.println(sc.getName());
                System.out.println("Mode = " + sc.getMode());
                System.out.println("State = " + sc.getState());
            }
        }

        System.out.println(result);
        assertTrue(outcome);
        assertTrue(result.hasDefined(FAILURE_DESCRIPTION));
        notificationHandler.validate(2);

        sc = container.getService(ServiceName.JBOSS.append("depended-service"));
        assertNotNull(sc);
        assertEquals(ServiceController.State.UP, sc.getState());

        sc = container.getService(ServiceName.JBOSS.append("dependent-service"));
        assertNotNull(sc);
        assertEquals(ServiceController.State.UP, sc.getState());

        // Confirm model was unchanged
        result = controller.execute(getOperation("good", "attr1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(5, result.get("result").asInt());
        notificationHandler.validate(0);
    }

    @Test
    public void testWildCardNavigation() throws Exception {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set("read-wildcards");
        operation.get(OP_ADDR).setEmptyList();
        operation.get("type").set("child");
        final ModelNode result = controller.execute(operation, null, null, null);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.get(RESULT).hasDefined("child"));
        assertEquals(2, result.get(RESULT, "child").asPropertyList().size());
    }

    @Test
    public void testRemoveServiceAfterNonRollbackServiceFailure() {

        // Phase I
        // First, a situation where the service gets installed but fails
        ModelNode operation = getOperation("bad-service", CHILD_ONE, "attribute1", 5);
        operation.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
        ModelNode result = controller.execute(operation, null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(FAILURE_DESCRIPTION));
        notificationHandler.validate(1);

        ServiceController<?> sc = container.getService(ServiceName.JBOSS.append("bad-service"));
        assertNotNull(sc);
        assertEquals(ServiceController.State.START_FAILED, sc.getState());

        // Confirm model *was* changed (since we didn't rollback)
        result = controller.execute(getOperation("read-attribute", CHILD_ONE, "attribute1", 1), null, null, null);
        assertEquals("success", result.get("outcome").asString());
        assertEquals(5, result.get("result").asInt());
        notificationHandler.validate(0);

        // Confirm we can still remove the resource
        result = controller.execute(getOperation("remove-bad-service", CHILD_ONE, "attribute1", 6), null, null, null);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        notificationHandler.validate(1);
        sc = container.getService(ServiceName.JBOSS.append("bad-service"));
        if (sc != null) {
            assertEquals(ServiceController.Mode.REMOVE, sc.getMode());
        }

        // Confirm the resource is gone
        operation = getOperation("read-attribute", CHILD_ONE, "attribute1", 1);
        result = controller.execute(operation, null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        notificationHandler.validate(0);
    }

    /**
     * Test for AS7-6104 and similar scenarios
     */
    @Test
    public void testRemoveServiceAfterNonRollbackRuntimeOFE() {

        // Phase II
        // Next mimic a situation where the service doesn't get installed at all (this is the AS7-6104 case)
        ModelNode operation = getOperation("operationFailedException", CHILD_ONE, "attribute1", 5);
        operation.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
        ModelNode result = controller.execute(operation, null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.get("failure-description").toString().contains("OFE"));
        notificationHandler.validate(0);

        // Confirm we can still remove the resource
        result = controller.execute(getOperation("remove-bad-service", CHILD_ONE, "attribute1", 6), null, null, null);
        System.out.println(result);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertFalse(result.get(RESULT).isDefined());
        notificationHandler.validate(1);

        // Confirm the resource is gone
        result = controller.execute(getOperation("read-attribute", CHILD_ONE, "attribute1", 1), null, null, null);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        notificationHandler.validate(0);
    }

    /**
     * WFCORE-2314 -- test handling of invalid values for common operation headers.
     *
     * Note, this test asserts particular failure messages. Those failure messags are not a strict
     * requirement. The test asserts these to ensure that the failure is what we expect. If we
     * deliberately change something such that the failures message are different on purpose, it's
     * ok to change this test to match.
     */
    @Test
    public void testCommonOperationHeaderValidation() {
        // Extract the desired header
        String msg = ControllerLogger.ROOT_LOGGER.incorrectType("a", EnumSet.of(ModelType.BOOLEAN), ModelType.STRING).getFailureDescription().asString();
        String msgCode = msg.substring(0, msg.indexOf(':'));

        ModelNode op = getOperation("good", "attr1", 5);

        op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set("true)");
        ModelNode result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), FAILED, result.get(OUTCOME).asString());
        assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().startsWith(msgCode));
        assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().contains(ROLLBACK_ON_RUNTIME_FAILURE));
        assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().contains("true)"));

        op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set("true");
        result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());

        op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(true);
        result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());

        op.get(OPERATION_HEADERS, ALLOW_RESOURCE_SERVICE_RESTART).set("true)");
        result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), FAILED, result.get(OUTCOME).asString());
        assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().startsWith(msgCode));
        assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().contains(ALLOW_RESOURCE_SERVICE_RESTART));
        assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().contains("true)"));

        op.get(OPERATION_HEADERS, ALLOW_RESOURCE_SERVICE_RESTART).set("true");
        result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());

        op.get(OPERATION_HEADERS, ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());

        op.get(OPERATION_HEADERS, BLOCKING_TIMEOUT).set("true)");
        result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), FAILED, result.get(OUTCOME).asString());
        assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().startsWith(msgCode));
        assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().contains(BLOCKING_TIMEOUT));
        assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().contains("true)"));

        op.get(OPERATION_HEADERS, BLOCKING_TIMEOUT).set("1");
        result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());

        op.get(OPERATION_HEADERS, BLOCKING_TIMEOUT).set(1);
        result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());

        msg = ControllerLogger.MGMT_OP_LOGGER.invalidBlockingTimeout(-1L, BLOCKING_TIMEOUT).getMessage();

        op.get(OPERATION_HEADERS, BLOCKING_TIMEOUT).set(-1);
        result = controller.execute(op, null, null, null);
        assertEquals(result.toString(), FAILED, result.get(OUTCOME).asString());
        assertEquals(result.toString(), msg, result.get(FAILURE_DESCRIPTION).asString());
    }

    @Test
    public void testDeprecatedOpWarning() {
        ModelNode rootOp = Util.createEmptyOperation("deprecated-op", PathAddress.EMPTY_ADDRESS);
        ModelNode rootResponse = controller.execute(rootOp, null, null, null);
        // Confirm all 3 steps ran
        Assert.assertEquals(rootResponse.toString(), 3, rootResponse.get(RESULT).asInt());
        ModelNode rootWarning = validateDeprecatedWarning(rootResponse, PathAddress.EMPTY_ADDRESS);

        // Execute against non-root
        PathAddress childAddr = PathAddress.EMPTY_ADDRESS.append(CHILD_ONE);
        ModelNode childOp = Util.createEmptyOperation("deprecated-op", childAddr);
        ModelNode childResponse = controller.execute(childOp, null, null, null);
        Assert.assertEquals(childResponse.toString(), 3, childResponse.get(RESULT).asInt());
        ModelNode childWarning = validateDeprecatedWarning(childResponse, childAddr);

        ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        composite.get(STEPS).add(rootOp);
        composite.get(STEPS).add(childOp);
        ModelNode compositeResponse = controller.execute(composite, null, null, null);

        String rspString = compositeResponse.toString();
        // TODO the warnings for composite ops aren't structured as checked below; that may be a bug
//        Assert.assertTrue(rspString, compositeResponse.hasDefined(RESPONSE_HEADERS, WARNINGS));
//        Assert.assertEquals(rspString, ModelType.LIST, compositeResponse.get(RESPONSE_HEADERS, WARNINGS).getType());
//        ModelNode warnings = compositeResponse.get(RESPONSE_HEADERS, WARNINGS);
//        Assert.assertEquals(rspString, 2, warnings.asInt()); // one warn per op
//        Assert.assertEquals(rspString, rootWarning, warnings.get(0));
//        Assert.assertEquals(rspString, childWarning, warnings.get(1));
        // Instead we validate that each step response is like the non-composite equivalent
        // It's ok to change this if we decide the way response headers for composites work is incorrect
        Assert.assertEquals(rspString, rootWarning, validateDeprecatedWarning(compositeResponse.get(RESULT, "step-1"), PathAddress.EMPTY_ADDRESS));
        Assert.assertEquals(rspString, childWarning, validateDeprecatedWarning(compositeResponse.get(RESULT, "step-2"), childAddr));
    }

    private static ModelNode validateDeprecatedWarning(ModelNode response, PathAddress address) {
        String rspString = response.toString();
        Assert.assertTrue(rspString, response.hasDefined(RESPONSE_HEADERS, WARNINGS));
        Assert.assertEquals(rspString, ModelType.LIST, response.get(RESPONSE_HEADERS, WARNINGS).getType());
        ModelNode warnings = response.get(RESPONSE_HEADERS, WARNINGS);
        Assert.assertEquals(rspString, 1, warnings.asInt()); // just one in list
        ModelNode warning = warnings.get(0);
        Assert.assertTrue(rspString, warning.get(WARNING).asString().contains("WFLYCTL0449"));
        Assert.assertEquals(rspString, "deprecated-op", warning.get(OP, OP).asString());
        Assert.assertEquals(rspString, "WARNING", warning.get(LEVEL).asString());
        Assert.assertEquals(rspString, address, PathAddress.pathAddress(warning.get(OP, OP_ADDR)));

        return warning;
    }

    public static ModelNode getOperation(String opName, String attr, int val) {
        return getOperation(opName, attr, val, null, false);
    }

    public static ModelNode getOperation(String opName, PathAddress address, String attr, int val) {
        return getOperation(opName, address, attr, val, null, false);
    }

    public static ModelNode getOperation(String opName, String attr, int val, String rollbackName) {
        return getOperation(opName, attr, val, rollbackName, false);
    }

    public static ModelNode getOperation(String opName, String attr, int val, String rollbackName, boolean async) {
        return getOperation(opName, PathAddress.EMPTY_ADDRESS, attr, val, rollbackName, async);
    }

    public static ModelNode getOperation(String opName, PathAddress address, String attr, int val, String rollbackName, boolean async) {
        ModelNode op = new ModelNode();
        op.get(OP).set(opName);
        op.get(OP_ADDR).set(address.toModelNode());
        op.get(NAME).set(attr);
        op.get(VALUE).set(val);
        op.get("rollbackName").set(rollbackName == null ? opName : rollbackName);

        if (async) {
            op.get("async").set(true);
        }
        return op;
    }

    static class ModelControllerService extends TestModelControllerService {

        @Override
        protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
            ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
            rootRegistration.registerOperationHandler(getOD("setup"), new ModelControllerImplUnitTestCase.SetupHandler(),true);
            rootRegistration.registerOperationHandler(getOD("composite"), CompositeOperationHandler.INSTANCE,true);
            rootRegistration.registerOperationHandler(getOD("good"), new ModelControllerImplUnitTestCase.ModelStageGoodHandler(),true);
            rootRegistration.registerOperationHandler(getOD("bad"), new ModelControllerImplUnitTestCase.ModelStageFailsHandler(),true);
            rootRegistration.registerOperationHandler(getOD("evil"), new ModelControllerImplUnitTestCase.ModelStageThrowsExceptionHandler(),true);
            rootRegistration.registerOperationHandler(getOD("handleFailed"), new ModelControllerImplUnitTestCase.RuntimeStageFailsHandler(state),true);
            rootRegistration.registerOperationHandler(getOD("runtimeException"), new ModelControllerImplUnitTestCase.RuntimeStageThrowsExceptionHandler(state),true);
            rootRegistration.registerOperationHandler(getOD("operationFailedException"), new ModelControllerImplUnitTestCase.RuntimeStageThrowsOFEHandler(),true);
            rootRegistration.registerOperationHandler(getOD("good-service"), new ModelControllerImplUnitTestCase.GoodServiceHandler(),true);
            rootRegistration.registerOperationHandler(getOD("bad-service"), new ModelControllerImplUnitTestCase.BadServiceHandler(),true);
            rootRegistration.registerOperationHandler(getOD("remove-bad-service"), new ModelControllerImplUnitTestCase.RemoveBadServiceHandler(),true);
            rootRegistration.registerOperationHandler(getOD("missing-service"), new ModelControllerImplUnitTestCase.MissingServiceHandler(),true);
            rootRegistration.registerOperationHandler(getOD("reload-required"), new ModelControllerImplUnitTestCase.ReloadRequiredHandler(),true);
            rootRegistration.registerOperationHandler(getOD("restart-required"), new ModelControllerImplUnitTestCase.RestartRequiredHandler(),true);
            rootRegistration.registerOperationHandler(getOD("dependent-service"), new ModelControllerImplUnitTestCase.DependentServiceHandler(),true);
            rootRegistration.registerOperationHandler(getOD("remove-dependent-service"), new ModelControllerImplUnitTestCase.RemoveDependentServiceHandler(),true);
            rootRegistration.registerOperationHandler(getOD("read-wildcards"), new ModelControllerImplUnitTestCase.WildcardReadHandler(),true);
            rootRegistration.registerOperationHandler(getOD("invalid-service-update"), new ModelControllerImplUnitTestCase.InvalidServiceUpdateHandler(),true);
            rootRegistration.registerOperationHandler(getODBuilder("deprecated-op").setDeprecated(ModelVersion.create(1)).build(), new DeprecatedHandler(), true);

            GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);

            GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

            rootRegistration.registerReadOnlyAttribute(TestUtils.createNillableAttribute("attr1", ModelType.INT), null);
            rootRegistration.registerReadOnlyAttribute(TestUtils.createNillableAttribute("attr2", ModelType.INT), null);

            SimpleResourceDefinition childResource = new SimpleResourceDefinition(
                    PathElement.pathElement("child"),
                    NonResolvingResourceDescriptionResolver.INSTANCE
            );
            ManagementResourceRegistration childRegistration = rootRegistration.registerSubModel(childResource);
            childRegistration.registerReadOnlyAttribute(TestUtils.createNillableAttribute("attribute1", ModelType.INT), null);
            childRegistration.registerReadOnlyAttribute(TestUtils.createNillableAttribute("attribute2", ModelType.INT, true), null);
            SimpleResourceDefinition runtimeChildResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(
                    PathElement.pathElement("runtime-child"),
                    NonResolvingResourceDescriptionResolver.INSTANCE
            ).setRuntime(true));
            rootRegistration.registerSubModel(runtimeChildResource);
            SimpleResourceDefinition deploymentResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(
                    PathElement.pathElement("deployment"),
                    NonResolvingResourceDescriptionResolver.INSTANCE
            ).setRuntime(true));
            rootRegistration.registerSubModel(deploymentResource);
        }

    }

    public static class SetupHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) {
            ModelNode model = new ModelNode();

            //Atttributes
            model.get("attr1").set(1);
            model.get("attr2").set(2);

            context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().set(model);

            final ModelNode child1 = new ModelNode();
            child1.get("attribute1").set(1);
            final ModelNode child2 = new ModelNode();
            child2.get("attribute2").set(2);

            context.createResource(CHILD_ONE).getModel().set(child1);
            context.createResource(CHILD_TWO).getModel().set(child2);
            final ModelNode deployment = new ModelNode();
            deployment.get("attribute1").set(5);
            context.createResource(PathAddress.pathAddress("deployment", "runtime")).getModel().set(deployment);
        }
    }

    public static class ModelStageGoodHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require(NAME).asString();
            ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
            ModelNode attr = model.get(name);
            final int current = attr.asInt();
            attr.set(operation.require(VALUE));

            context.getResult().set(current);

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    public static class ModelStageFailsHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require(NAME).asString();
            ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
            ModelNode attr = model.get(name);
            attr.set(operation.require(VALUE));

            context.getFailureDescription().set("this request is bad");

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    public static class ModelStageThrowsExceptionHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require(NAME).asString();
            ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
            ModelNode attr = model.get(name);
            attr.set(operation.require(VALUE));

            throw new RuntimeException("this handler is evil");
        }
    }

    public static class RuntimeStageFailsHandler implements OperationStepHandler {

        private final AtomicBoolean state;

        public RuntimeStageFailsHandler(AtomicBoolean state) {
            this.state = state;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require("name").asString();
            ModelNode attr = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(name);
            int current = attr.asInt();
            attr.set(operation.require("value"));

            context.getResult().set(current);

            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(OperationContext context, ModelNode operation) {
                    toggleRuntimeState(state);
                    context.getFailureDescription().set("handleFailed");

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            toggleRuntimeState(state);
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    public static class RuntimeStageThrowsExceptionHandler implements OperationStepHandler {

        private final AtomicBoolean state;

        public RuntimeStageThrowsExceptionHandler(AtomicBoolean state) {
            this.state = state;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require("name").asString();
            ModelNode attr = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(name);
            int current = attr.asInt();
            attr.set(operation.require("value"));

            context.getResult().set(current);

            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(OperationContext context, ModelNode operation) {
                    toggleRuntimeState(state);
                    throw new RuntimeException("runtime exception");
                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    public static class RuntimeStageThrowsOFEHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require("name").asString();
            ModelNode attr = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(name);
            int current = attr.asInt();
            attr.set(operation.require("value"));

            context.getResult().set(current);

            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    throw new OperationFailedException("OFE");
                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    public static class GoodServiceHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require("name").asString();
            ModelNode attr = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(name);
            final int current = attr.asInt();
            attr.set(operation.require("value"));

            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(final OperationContext context, ModelNode operation) {

                    context.getResult().set(current);
                    final ServiceName svcName =  ServiceName.JBOSS.append("good-service");
                    context.getServiceTarget().addService(svcName).install();

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            context.removeService(svcName);
                        }
                    });

                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    public static class MissingServiceHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require("name").asString();
            ModelNode attr = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(name);
            final int current = attr.asInt();
            attr.set(operation.require("value"));

            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(final OperationContext context, ModelNode operation) {

                    context.getResult().set(current);

                    final ServiceName svcName = ServiceName.JBOSS.append("missing-service");
                    final ServiceBuilder sb = context.getServiceTarget().addService(svcName);
                    sb.requires(ServiceName.JBOSS.append("missing"));
                    sb.install();

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            context.removeService(svcName);
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    public static class BadServiceHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require("name").asString();
            ModelNode attr = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(name);
            final int current = attr.asInt();
            attr.set(operation.require("value"));

            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(final OperationContext context, ModelNode operation) {

                    context.getResult().set(current);


                    Service<Void> bad = new Service<Void>() {

                        @Override
                        public Void getValue() throws IllegalStateException, IllegalArgumentException {
                            return null;
                        }

                        @Override
                        public void start(StartContext context) throws StartException {
                            throw new RuntimeException("Bad service!");
                        }

                        @Override
                        public void stop(StopContext context) {
                        }

                    };
                    final ServiceName svcName = ServiceName.JBOSS.append("bad-service");
                    context.getServiceTarget().addService(svcName).setInstance(bad)
                            .install();

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            context.removeService(svcName);
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    public static class RemoveBadServiceHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context, ModelNode operation) {

            context.removeResource(PathAddress.EMPTY_ADDRESS);

            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(final OperationContext context, ModelNode operation) {

                    final ServiceName svcName = ServiceName.JBOSS.append("bad-service");
                    final ServiceRegistry sr = context.getServiceRegistry(true);
                    ServiceController<?> sc = sr.getService(svcName);
                    context.removeService(sc);

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            context.getResult().set("Unexpected rollback");
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    public static class ReloadRequiredHandler implements OperationStepHandler {
        @Override
        public void execute(final OperationContext context, ModelNode operation) {

            String name = operation.require(NAME).asString();
            ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
            ModelNode attr = model.get(name);
            final int current = attr.asInt();
            attr.set(operation.require(VALUE));

            context.getResult().set(current);

            context.runtimeUpdateSkipped();
            context.reloadRequired();

            context.completeStep(new OperationContext.RollbackHandler() {
                @Override
                public void handleRollback(OperationContext context, ModelNode operation) {
                    context.revertReloadRequired();
                }
            });
        }
    }

    public static class RestartRequiredHandler implements OperationStepHandler {
        @Override
        public void execute(final OperationContext context, ModelNode operation) {

            String name = operation.require(NAME).asString();
            ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
            ModelNode attr = model.get(name);
            final int current = attr.asInt();
            attr.set(operation.require(VALUE));

            context.getResult().set(current);

            context.runtimeUpdateSkipped();
            context.restartRequired();

            context.completeStep(new OperationContext.RollbackHandler() {
                @Override
                public void handleRollback(OperationContext context, ModelNode operation) {
                    context.revertRestartRequired();
                }
            });
        }
    }

    public static class DependentServiceHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require("name").asString();
            ModelNode attr = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(name);
            final int current = attr.asInt();
            attr.set(operation.require("value"));

            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(final OperationContext context, ModelNode operation) {

                    context.getResult().set(current);
                    final ServiceName dependedSvcName = ServiceName.JBOSS.append("depended-service");
                    context.getServiceTarget().addService(dependedSvcName).install();
                    final ServiceName dependentSvcName = ServiceName.JBOSS.append("dependent-service");
                    final ServiceBuilder sb = context.getServiceTarget().addService(dependentSvcName);
                    sb.requires(dependedSvcName);
                    sb.install();

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            context.removeService(dependedSvcName);
                            context.removeService(dependentSvcName);
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    public static class RemoveDependentServiceHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context, ModelNode operation) {

            String name = operation.require("name").asString();
            ModelNode attr = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(name);
            final int current = attr.asInt();
            attr.set(operation.require("value"));

            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(final OperationContext context, ModelNode operation) {

                    context.getResult().set(current);
                    final ServiceName dependedSvcName = ServiceName.JBOSS.append("depended-service");
                    context.removeService(dependedSvcName);

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                        context.getServiceTarget().addService(dependedSvcName).install();
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    static final class WildcardReadHandler implements OperationStepHandler {

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final String type = operation.require("type").asString();
            final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS.append(PathElement.pathElement(type)));
            context.getResult().set(Resource.Tools.readModel(resource));
        }

    }

    public static class InvalidServiceUpdateHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context,final ModelNode operation) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, ModelNode operation) {
                    final ServiceName svcName = ServiceName.JBOSS.append("good-service");
                    context.removeService(svcName);
                    final ServiceBuilder sb = context.getServiceTarget().addService(svcName);
                    sb.requires(ServiceName.JBOSS.append("missing"));
                    sb.install();

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            context.getServiceTarget().addService(svcName).install();
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    private static final class DeprecatedHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) {
            context.getResult().set(1);
            // Add child steps to prove that those steps don't result in duplicate warnings
            context.addStep((context1, operation1) -> context.getResult().set(2), OperationContext.Stage.MODEL);
            context.addStep((context1, operation1) -> context.getResult().set(3), OperationContext.Stage.RUNTIME);
        }
    }

    static class RollbackTransactionControl implements ModelController.OperationTransactionControl {

        static final RollbackTransactionControl INSTANCE = new RollbackTransactionControl();

        @Override
        public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode result) {
            transaction.rollback();
        }
    }

    private static class ServiceNotificationHandler implements NotificationHandler, NotificationFilter {

        private final List<String> events = new ArrayList<>();

        @Override
        public boolean isNotificationEnabled(Notification notification) {
            return RUNTIME_MODIFICATION_BEGUN.equals(notification.getType())
                    || RUNTIME_MODIFICATION_COMPLETE.equals(notification.getType());
        }

        @Override
        public void handleNotification(Notification notification) {
            events.add(notification.getType());
        }

        void validate(int expectedPairs) {
            assertEquals(events.toString(), expectedPairs * 2, events.size());
            for (int i = 0; i < events.size(); i++) {
                String expected = i % 2 == 0 ? RUNTIME_MODIFICATION_BEGUN : RUNTIME_MODIFICATION_COMPLETE;
                assertEquals(events.toString() + " " + i, expected, events.get(i));
            }
            events.clear();
        }
    }
}
