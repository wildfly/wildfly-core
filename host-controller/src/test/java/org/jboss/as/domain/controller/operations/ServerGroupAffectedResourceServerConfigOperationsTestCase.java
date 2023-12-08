/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessState.State;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.discovery.DiscoveryOption;
import org.jboss.as.host.controller.operations.ServerAddHandler;
import org.jboss.as.host.controller.operations.ServerRemoveHandler;
import org.jboss.as.host.controller.operations.ServerRestartRequiredServerConfigWriteAttributeHandler;
import org.jboss.as.host.controller.resources.ServerConfigResourceDefinition;
import org.jboss.as.process.ProcessInfo;
import org.jboss.as.process.ProcessMessageHandler;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.version.ProductConfig;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceRegistry;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerGroupAffectedResourceServerConfigOperationsTestCase extends AbstractOperationTestCase {

    @Test
    public void testAddServerConfigPrimary() throws Exception {
        testAddServerConfig(true, false);
    }

    @Test
    public void testAddServerConfigSecondary() throws Exception {
        testAddServerConfig(false, false);
    }

    @Test
    public void testAddServerConfigPrimaryRollback() throws Exception {
        testAddServerConfig(true, true);
    }

    @Test
    public void testAddServerConfigSecondaryRollback() throws Exception {
        testAddServerConfig(false, true);
    }

    private void testAddServerConfig(boolean primary, boolean rollback) throws Exception {
        testAddServerConfigBadInfo(primary, rollback, false, SocketBindingGroupOverrideType.GOOD);
    }

    @Test
    public void testAddServerConfigNoSocketBindingGroupOverridePrimary() throws Exception {
        testAddServerConfigNoSocketBindingGroupOverride(true, false);
    }

    @Test
    public void testAddServerConfigNoSocketBindingGroupOverrideSecondary() throws Exception {
        testAddServerConfigNoSocketBindingGroupOverride(false, false);
    }

    @Test
    public void testAddServerConfigNoSocketBindingGroupOverridePrimaryRollback() throws Exception {
        testAddServerConfigNoSocketBindingGroupOverride(true, true);
    }

    @Test
    public void testAddServerConfigNoSocketBindingGroupOverrideSecondaryRollback() throws Exception {
        testAddServerConfigNoSocketBindingGroupOverride(false, true);
    }

    private void testAddServerConfigNoSocketBindingGroupOverride(boolean primary, boolean rollback) throws Exception {
        testAddServerConfigBadInfo(primary, rollback, false, SocketBindingGroupOverrideType.NONE);
    }

//    // WFCORE-833 moved to ServerConfigTestCase.testAddServerConfigBadServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerConfigBadServerGroupePrimary() throws Exception {
//        testAddServerConfigBadInfo(true, false, true, SocketBindingGroupOverrideType.GOOD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testAddServerConfigBadServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerConfigBadServerGroupSecondary() throws Exception {
//        testAddServerConfigBadInfo(false, false, true, SocketBindingGroupOverrideType.GOOD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testAddServerConfigBadServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerConfigBadServerGroupPrimaryRollback() throws Exception {
//        //This won't actually get to the rollback part
//        testAddServerConfigBadInfo(true, true, true, SocketBindingGroupOverrideType.GOOD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testAddServerConfigBadServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerConfigBadServerGroupSecondaryRollback() throws Exception {
//        testAddServerConfigBadInfo(false, true, true, SocketBindingGroupOverrideType.GOOD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testAddServerConfigBadSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerConfigBadSocketBindingGroupOverridePrimary() throws Exception {
//        testAddServerConfigBadInfo(true, false, false, SocketBindingGroupOverrideType.BAD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testAddServerConfigBadSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerConfigBadSocketBindingGroupOverrideSecondary() throws Exception {
//        testAddServerConfigBadInfo(false, false, false, SocketBindingGroupOverrideType.BAD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testAddServerConfigBadSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerConfigBadSocketBindingGroupOverridePrimaryRollback() throws Exception {
//        //This won't actually get to the rollback part
//        testAddServerConfigBadInfo(true, true, false, SocketBindingGroupOverrideType.BAD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testAddServerConfigBadSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testAddServerConfigBadSocketBindingGroupOverrideSecondaryRollback() throws Exception {
//        testAddServerConfigBadInfo(false, true, false, SocketBindingGroupOverrideType.BAD);
//    }

    private void testAddServerConfigBadInfo(boolean primary, boolean rollback, boolean badServerGroup, SocketBindingGroupOverrideType socketBindingGroupOverride) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-four"));
        final MockOperationContext operationContext = new MockOperationContext(pa, rollback);

        String serverGroupName = badServerGroup ? "bad-server-group" : "group-one";
        String socketBindingGroupName;
        if (socketBindingGroupOverride == SocketBindingGroupOverrideType.GOOD) {
            socketBindingGroupName = "binding-two";
        } else if (socketBindingGroupOverride == SocketBindingGroupOverrideType.BAD) {
            socketBindingGroupName = "bad-socket-binding-group";
        } else {
            socketBindingGroupName = null;
        }

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(ADD);
        operation.get(GROUP).set(serverGroupName);
        if (socketBindingGroupName != null) {
            operation.get(SOCKET_BINDING_GROUP).set(socketBindingGroupName);
        }

        try {
            operationContext.executeStep(ServerAddHandler.create(new MockHostControllerInfo(primary), new ServerInventoryMock(), new ControlledProcessState(false), new File(System.getProperty("java.io.tmpdir"))), operation);
        } catch (RuntimeException e) {
            final Throwable t = e.getCause();
            if (t instanceof OperationFailedException) {
                throw (OperationFailedException) t;
            }
            throw e;
        }

        if (primary && (socketBindingGroupOverride == SocketBindingGroupOverrideType.BAD || badServerGroup)) {
            Assert.fail();
        }

        Assert.assertFalse(operationContext.isReloadRequired());
    }


    @Test
    public void testRemoveServerConfigPrimary() throws Exception {
        testRemoveServerConfig(true, false);
    }

    @Test
    public void testRemoveServerConfigSecondary() throws Exception {
        testRemoveServerConfig(false, false);
    }

    @Test
    public void testRemoveServerConfigPrimaryRollback() throws Exception {
        testRemoveServerConfig(true, true);
    }

    @Test
    public void testRemoveServerConfigSecondaryRollback() throws Exception {
        testRemoveServerConfig(false, true);
    }

    private void testRemoveServerConfig(boolean primary, boolean rollback) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = new MockOperationContext(pa, rollback);

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(REMOVE);

        ServerRemoveHandler.INSTANCE.execute(operationContext, operation);

        Assert.assertFalse(operationContext.isReloadRequired());
    }

    @Test
    public void testUpdateServerConfigServerGroupPrimary() throws Exception {
        testUpdateServerConfigServerGroup(true, false, false);
    }

    @Test
    public void testUpdateServerConfigServerGroupSecondary() throws Exception {
        testUpdateServerConfigServerGroup(false, false, false);

    }

    @Test
    public void testUpdateServerConfigServerGroupPrimaryRollback() throws Exception {
        testUpdateServerConfigServerGroup(true, true, false);

    }

    @Test
    public void testUpdateServerConfigServerGroupSecondaryRollback() throws Exception {
        testUpdateServerConfigServerGroup(false, true, false);
    }


//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerConfigBadServerGroupPrimary() throws Exception {
//        testUpdateServerConfigServerGroup(true, false, true);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerConfigBadServerGroupSecondary() throws Exception {
//        testUpdateServerConfigServerGroup(false, false, true);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerConfigBadServerGroupPrimaryRollback() throws Exception {
//        testUpdateServerConfigServerGroup(true, true, true);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidServerGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerConfigBadServerGroupSecondaryRollback() throws Exception {
//        testUpdateServerConfigServerGroup(false, true, true);
//    }

    private void testUpdateServerConfigServerGroup(boolean primary, boolean rollback, boolean badGroup) throws Exception {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = new MockOperationContext(pa, rollback);

        String groupName = badGroup ? "bad-group" : "group-two";

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(GROUP);
        operation.get(VALUE).set(groupName);

        try {
            operationContext.executeStep(ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE, operation);
        } catch (RuntimeException e) {
            final Throwable t = e.getCause();
            if (t instanceof OperationFailedException) {
                throw (OperationFailedException) t;
            }
            throw e;
        }

        if (primary && badGroup) {
            //primary will throw an exception
            Assert.fail();
        }

        Assert.assertFalse(operationContext.isReloadRequired());
    }

    @Test
    public void testUpdateServerConfigSocketBindingGroupPrimary() throws Exception {
        testUpdateServerConfigSocketBindingGroup(true, false, SocketBindingGroupOverrideType.GOOD);
    }

    @Test
    public void testUpdateServerConfigSocketBindingGroupSecondary() throws Exception {
        testUpdateServerConfigSocketBindingGroup(false, false, SocketBindingGroupOverrideType.GOOD);

    }

    @Test
    public void testUpdateServerConfigSocketBindingGroupPrimaryRollback() throws Exception {
        testUpdateServerConfigSocketBindingGroup(true, true, SocketBindingGroupOverrideType.GOOD);

    }

    @Test
    public void testUpdateServerConfigSocketBindingGroupSecondaryRollback() throws Exception {
        testUpdateServerConfigSocketBindingGroup(false, true, SocketBindingGroupOverrideType.GOOD);
    }


//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerConfigBadSocketBindingGroupPrimary() throws Exception {
//        testUpdateServerConfigSocketBindingGroup(true, false, SocketBindingGroupOverrideType.BAD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerConfigBadSocketBindingGroupSecondary() throws Exception {
//        testUpdateServerConfigSocketBindingGroup(false, false, SocketBindingGroupOverrideType.BAD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerConfigBadSocketBindingGroupPrimaryRollback() throws Exception {
//        testUpdateServerConfigSocketBindingGroup(true, true, SocketBindingGroupOverrideType.BAD);
//    }

//    // WFCORE-833 moved to ServerConfigTestCase.testChangeServerGroupInvalidSocketBindingGroup()
//    @Test(expected=OperationFailedException.class)
//    public void testUpdateServerConfigBadSocketBindingGroupSecondaryRollback() throws Exception {
//        testUpdateServerConfigSocketBindingGroup(false, true, SocketBindingGroupOverrideType.BAD);
//    }

    @Test
    public void testUpdateServerConfigNoSocketBindingGroupPrimary() throws Exception {
        testUpdateServerConfigSocketBindingGroup(true, false, SocketBindingGroupOverrideType.NONE);
    }

    @Test
    public void testUpdateServerConfigNoSocketBindingGroupSecondary() throws Exception {
        testUpdateServerConfigSocketBindingGroup(false, false, SocketBindingGroupOverrideType.NONE);
    }

    @Test
    public void testUpdateServerConfigNoSocketBindingGroupPrimaryRollback() throws Exception {
        testUpdateServerConfigSocketBindingGroup(true, true, SocketBindingGroupOverrideType.NONE);
    }

    @Test
    public void testUpdateServerConfigNoSocketBindingGroupSecondaryRollback() throws Exception {
        testUpdateServerConfigSocketBindingGroup(false, true, SocketBindingGroupOverrideType.NONE);
    }

    private void testUpdateServerConfigSocketBindingGroup(boolean primary, boolean rollback, SocketBindingGroupOverrideType socketBindingGroupOverride) throws Exception {

        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, "localhost"), PathElement.pathElement(SERVER_CONFIG, "server-one"));
        final MockOperationContext operationContext = new MockOperationContext(pa, rollback);

        String socketBindingGroupName;
        if (socketBindingGroupOverride == SocketBindingGroupOverrideType.GOOD) {
            socketBindingGroupName = "binding-two";
        } else if (socketBindingGroupOverride == SocketBindingGroupOverrideType.BAD) {
            socketBindingGroupName = "bad-socket-binding-group";
        } else {
            socketBindingGroupName = null;
        }

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(pa.toModelNode());
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(SOCKET_BINDING_GROUP);
        operation.get(VALUE).set(socketBindingGroupName != null ? new ModelNode(socketBindingGroupName) : new ModelNode());

        try {
            operationContext.executeStep(ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE, operation);
        } catch (RuntimeException e) {
            final Throwable t = e.getCause();
            if (t instanceof OperationFailedException) {
                throw (OperationFailedException) t;
            }
            throw e;
        }

        if (primary && socketBindingGroupOverride == SocketBindingGroupOverrideType.BAD) {
            //primary will throw an exception
            Assert.fail();
        }

        Assert.assertFalse(operationContext.isReloadRequired());
    }

    private static class MockHostControllerInfo implements LocalHostControllerInfo {
        private final boolean primary;

        public MockHostControllerInfo(boolean primary) {
            this.primary = primary;
        }

        @Override
        public String getLocalHostName() {
            return null;
        }

        @Override
        public boolean isMasterDomainController() {
            return primary;
        }

        @Override
        public String getNativeManagementInterface() {
            return null;
        }

        @Override
        public int getNativeManagementPort() {
            return 0;
        }

        @Override
        public String getHttpManagementInterface() {
            return null;
        }

        @Override
        public int getHttpManagementPort() {
            return 0;
        }

        @Override
        public String getHttpManagementSecureInterface() {
            return null;
        }

        @Override
        public int getHttpManagementSecurePort() {
            return 0;
        }

        @Override
        public String getRemoteDomainControllerUsername() {
            return null;
        }

        @Override
        public List<DiscoveryOption> getRemoteDomainControllerDiscoveryOptions() {
            return null;
        }

        @Override
        public State getProcessState() {
            return null;
        }

        @Override
        public boolean isRemoteDomainControllerIgnoreUnaffectedConfiguration() {
            return true;
        }

        @Override
        public boolean isBackupDc() {
            return false;
        }

        @Override
        public boolean isUsingCachedDc() {
            return false;
        }

        @Override
        public Stability getStability() {
            return Stability.DEFAULT;
        }

        @Override
        public ProductConfig getProductConfig() {
            return null;
        }
    }

    private static class ServerInventoryMock implements ServerInventory {

        public ServerInventoryMock() {
        }

        @Override
        public String getServerProcessName(String serverName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String getProcessServerName(String processName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Map<String, ProcessInfo> determineRunningProcesses() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Map<String, ProcessInfo> determineRunningProcesses(boolean serversOnly) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ServerStatus determineServerStatus(String serverName) {
            return ServerStatus.STARTED;
        }

        @Override
        public ServerStatus startServer(String serverName, ModelNode domainModel) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ServerStatus startServer(String serverName, ModelNode domainModel, boolean blocking, boolean suspend) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ServerStatus restartServer(String serverName, int gracefulTimeout, ModelNode domainModel) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ServerStatus restartServer(String serverName, int gracefulTimeout, ModelNode domainModel, boolean blocking, boolean suspend) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ServerStatus stopServer(String serverName, int gracefulTimeout) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ServerStatus stopServer(String serverName, int gracefulTimeout, boolean blocking) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void stopServers(int gracefulTimeout) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void stopServers(int gracefulTimeout, boolean blockUntilStopped) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void reconnectServer(String serverName, ModelNode domainModel, boolean running, boolean stopping) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ServerStatus reloadServer(String serverName, boolean blocking, boolean suspend) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void destroyServer(String serverName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void killServer(String serverName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ProxyController serverCommunicationRegistered(String serverProcessName, ManagementChannelHandler channelHandler) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean serverReconnected(String serverProcessName, ManagementChannelHandler channelHandler) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void serverStarted(String serverProcessName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void serverStartFailed(String serverProcessName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void serverUnstable(String serverProcessName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void serverProcessStopped(String serverProcessName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void connectionFinished() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void serverProcessAdded(String processName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void serverProcessStarted(String processName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void serverProcessRemoved(String processName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void operationFailed(String processName, ProcessMessageHandler.OperationType type) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void processInventory(Map<String, ProcessInfo> processInfos) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void awaitServersState(Collection<String> serverNames, boolean started) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public List<ModelNode> suspendServers(Set<String> serverNames, BlockingTimeout blockingTimeout) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public List<ModelNode> resumeServers(Set<String> serverNames, BlockingTimeout blockingTimeout) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public List<ModelNode> suspendServers(Set<String> serverNames, int timeout, BlockingTimeout blockingTimeout) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }

    private class MockOperationContext extends AbstractOperationTestCase.MockOperationContext {
        private boolean reloadRequired;
        private boolean rollback;
        private final Queue<OperationStepHandler> nextHandlers = new ArrayDeque<>();

        protected MockOperationContext(PathAddress operationAddress, boolean rollback) {
            super(createRootResource(), false, operationAddress, ServerConfigResourceDefinition.WRITABLE_ATTRIBUTES.toArray(AttributeDefinition[]::new));
            this.rollback = rollback;
            when(this.registration.getCapabilities()).thenReturn(Collections.singleton(ServerConfigResourceDefinition.SERVER_CONFIG_CAPABILITY));
        }

        void executeStep(OperationStepHandler handler, ModelNode operation) throws OperationFailedException {
            handler.execute(this, operation);
            completed();
        }

        @Override
        public void completeStep(ResultHandler resultHandler) {
            if (!nextHandlers.isEmpty()) {
                completed();
            } else if (rollback) {
                resultHandler.handleResult(ResultAction.ROLLBACK, this, null);
            }
        }

        private void completed() {
            if (!nextHandlers.isEmpty()) {
                try {
                    OperationStepHandler step = nextHandlers.poll();
                    step.execute(this, null);
                } catch (OperationFailedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void reloadRequired() {
            reloadRequired = true;
        }

        @Override
        public boolean isReloadRequired() {
            return reloadRequired;
        }

        @Override
        public void revertReloadRequired() {
            reloadRequired = false;
        }

        @Override
        public void addStep(OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            nextHandlers.add(step);
        }

        @Override
        public void addStep(ModelNode operation, OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            if (operation.get(OP).asString().equals("verify-running-server")) {
                return;
            }
            super.addStep(operation, step, stage);
        }

        @Override
        public boolean isBooting() {
            return false;
        }

        @Override
        public ServiceRegistry getServiceRegistry(boolean modify) throws UnsupportedOperationException {
            return null;
        }
    }

    private enum SocketBindingGroupOverrideType {
        GOOD,
        BAD,
        NONE
    }
}
