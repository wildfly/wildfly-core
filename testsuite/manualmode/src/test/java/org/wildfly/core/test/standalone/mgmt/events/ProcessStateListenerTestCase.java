/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.wildfly.core.test.standalone.mgmt.events;

import static org.hamcrest.CoreMatchers.is;
import static org.wildfly.extension.core.management.client.Process.RunningMode.ADMIN_ONLY;
import static org.wildfly.extension.core.management.client.Process.RunningMode.NORMAL;
import static org.wildfly.extension.core.management.client.Process.Type.STANDALONE_SERVER;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.repository.PathUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.Server;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.extension.core.management.client.Process;
import org.wildfly.test.events.provider.TestListener;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ProcessStateListenerTestCase {

    private static final PathAddress LISTENER_ADDRESS = PathAddress.EMPTY_ADDRESS
            .append("subsystem", "core-management")
            .append("process-state-listener", "my-listener");
    private static Path runtimeConfigurationStateChangeFile;
    private static Path runningStateChangeFile;
    private static TestModule module;
    private static Path data;

    @Inject
    private static ServerController controller;

    @BeforeClass
    public static void setup() throws Exception {
        data = Files.createTempDirectory("notifications");
        runtimeConfigurationStateChangeFile = data.resolve(TestListener.RUNTIME_CONFIGURATION_STATE_CHANGE_FILENAME);
        runningStateChangeFile = data.resolve(TestListener.RUNNING_STATE_CHANGE_FILENAME);
        module = createModule();
        addListener();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        try {
            controller.startInAdminMode();
            DomainTestUtils.executeForResult(Util.createRemoveOperation(LISTENER_ADDRESS), controller.getClient().getControllerClient());
        } finally {
            controller.stop();
        }
        module.remove();
        PathUtil.deleteSilentlyRecursively(runtimeConfigurationStateChangeFile);
        PathUtil.deleteSilentlyRecursively(runningStateChangeFile);
    }

    private static TestModule createModule() throws IOException, URISyntaxException {
        // Get module.xml, create modules.jar and add to test config
        final File moduleXml = getModuleXml("process-state-listener-module.xml");
        TestModule module = new TestModule(TestListener.class.getPackage().getName(), moduleXml);
        module.addResource("process-state-listener.jar").addPackage(TestListener.class.getPackage());
        module.create(true);
        return module;
    }

    private static File getModuleXml(final String name) throws URISyntaxException {
        // Get the module xml
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return new File(tccl.getResource("extension/" + name).toURI());
    }

    private static void addListener() throws Exception {
        try {
            controller.startInAdminMode();
            ModelNode addListener = Util.createAddOperation(LISTENER_ADDRESS);
            addListener.get("class").set(TestListener.class.getName());
            addListener.get("module").set(TestListener.class.getPackage().getName());
            ModelNode props = new ModelNode();
            props.add("file", data.toAbsolutePath().toString());
            addListener.get("properties").set(props);
            DomainTestUtils.executeForResult(addListener, controller.getClient().getControllerClient());
        } finally {
            controller.stop();
        }
    }

    @Before
    public void clearNotificationFiles() throws Exception {
        Files.delete(runtimeConfigurationStateChangeFile);
        Files.createFile(runtimeConfigurationStateChangeFile);
        Files.delete(runningStateChangeFile);
        Files.createFile(runningStateChangeFile);
    }

    @Test
    public void testListenerStartInAdminOnly() throws Exception {
        try {
            controller.startInAdminMode();
            controller.stop();
            controller.startInAdminMode();
            controller.reload(true, 30 * 1000);
            controller.reload();
            controller.reload(true, 30 * 1000);
        } finally {
            controller.stop();
        }

        RuntimeConfigurationStateChanges runtimeConfigChanges = new RuntimeConfigurationStateChanges(runtimeConfigurationStateChangeFile);
        // start to admin_only
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // stop
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        // start to admin_only
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // reload to admin only
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // reload to normal mode
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // reload to admin only
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // stop
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        runtimeConfigChanges.verify();

        RunningStateChanges runningStateChanges = new RunningStateChanges(runningStateChangeFile);
        // start to admin_only
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.SUSPENDED, Process.RunningState.ADMIN_ONLY);
        // stop
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.ADMIN_ONLY, Process.RunningState.SUSPENDING);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.SUSPENDED, Process.RunningState.STOPPING);
        // start to admin_only
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.SUSPENDED, Process.RunningState.ADMIN_ONLY);
        // reload to admin only
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.ADMIN_ONLY, Process.RunningState.STOPPING);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.SUSPENDED, Process.RunningState.ADMIN_ONLY);
        // reload to normal mode
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.ADMIN_ONLY, Process.RunningState.STOPPING);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.NORMAL);
        // reload to admin only
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.STOPPING);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.SUSPENDED, Process.RunningState.ADMIN_ONLY);
        // stop
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.ADMIN_ONLY, Process.RunningState.SUSPENDING);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.SUSPENDED, Process.RunningState.STOPPING);
        runningStateChanges.verify();
    }

    @Test
    public void testListenerStartInNormal() throws Exception {
        try {
            controller.start();
            controller.stop();
            controller.start();
            controller.reload(true, 30 * 1000);
            controller.reload();
            suspendServer();
            resumeServer();
        } finally {
            controller.stop();
        }

        RuntimeConfigurationStateChanges runtimeConfigChanges = new RuntimeConfigurationStateChanges(runtimeConfigurationStateChangeFile);
        // start to normal
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // stop
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        // start to normal
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // reload to admin only
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // reload to normal mode
        runtimeConfigChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // stop
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        runtimeConfigChanges.verify();

        RunningStateChanges runningStateChanges = new RunningStateChanges(runningStateChangeFile);
        // start to normal
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.NORMAL);
        // stop
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.SUSPENDING);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.STOPPING);
        // start to normal
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.NORMAL);
        // reload to admin only
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.STOPPING);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.SUSPENDED, Process.RunningState.ADMIN_ONLY);
        // reload to normal mode
        runningStateChanges.add(STANDALONE_SERVER, ADMIN_ONLY, Process.RunningState.ADMIN_ONLY, Process.RunningState.STOPPING);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.NORMAL);
        // suspend
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.SUSPENDING);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
        // resume
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.NORMAL);
        // stop
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.SUSPENDING);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.STOPPING);
        runningStateChanges.verify();
    }

    @Test
    public void testListenerStartSuspended() throws Exception {
        try {
            controller.startSuspended();
            resumeServer();
            controller.reload(Server.StartMode.SUSPEND);
        } finally {
            controller.stop();
        }

        RuntimeConfigurationStateChanges runtimeConfigChanges = new RuntimeConfigurationStateChanges(runtimeConfigurationStateChangeFile);
        // start suspended
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // reload to suspend
        // reload to admin only
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
        // stop
        runtimeConfigChanges.add(STANDALONE_SERVER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
        runtimeConfigChanges.verify();

        RunningStateChanges runningStateChanges = new RunningStateChanges(runningStateChangeFile);
        // start suspended
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        // resume
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.NORMAL);
        // reload to suspend
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.STOPPING);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
        // stop bug in SuspendController ?
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.SUSPENDING);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
        runningStateChanges.add(STANDALONE_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.STOPPING);
        runningStateChanges.verify();
    }

    private void resumeServer() throws UnsuccessfulOperationException {
        final ModelNode resume = new ModelNode();
        resume.get(ClientConstants.OP).set("resume");
        controller.getClient().executeForResult(resume);
    }

    private void suspendServer() throws UnsuccessfulOperationException {
        final ModelNode resume = new ModelNode();
        resume.get(ClientConstants.OP).set("suspend");
        controller.getClient().executeForResult(resume);
    }

    private abstract static class StateChanges {

        protected List<String> changes;
        private final Path file;

        public StateChanges(Path file) {
            this.file = file;
            this.changes = new ArrayList<>();
        }

        public void verify() throws IOException {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            //MatcherAssert.assertThat(lines, Is.is(changes));
            for(int i = 0; i <lines.size(); i++) {
                Assert.assertThat("Incorrect match at line " + i, lines.get(i), is(changes.get(i)));
            }
        }
    }

    private static class RunningStateChanges extends StateChanges {

        public RunningStateChanges(Path file) {
            super(file);
        }

        public void add(Process.Type processType, Process.RunningMode runningMode,
                Process.RunningState oldState, Process.RunningState newState) {
            changes.add(processType + " " + runningMode + " " + oldState + " " + newState);
        }
    }

    private static class RuntimeConfigurationStateChanges extends StateChanges {

        public RuntimeConfigurationStateChanges(Path file) {
            super(file);
        }

        public void add(Process.Type processType, Process.RunningMode runningMode,
                Process.RuntimeConfigurationState oldState, Process.RuntimeConfigurationState newState) {
            changes.add(processType + " " + runningMode + " " + oldState + " " + newState);
        }
    }

}
