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
package org.jboss.as.test.integration.domain.events;


import static org.hamcrest.CoreMatchers.is;
import static org.wildfly.extension.core.management.client.Process.RunningMode.ADMIN_ONLY;
import static org.wildfly.extension.core.management.client.Process.RunningMode.NORMAL;
import static org.wildfly.extension.core.management.client.Process.Type.DOMAIN_SERVER;
import static org.wildfly.extension.core.management.client.Process.Type.HOST_CONTROLLER;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.repository.PathUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extension.core.management.client.Process;
import org.wildfly.test.events.provider.TestListener;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public class ProcessStateListenerTestCase {
    private static final PathAddress MAIN_SERVER_GROUP_ADDRESS = PathAddress.pathAddress("server-group", "main-server-group");
    private static Path data;
    private static Path runtimeConfigurationStateChangeFile;
    private static Path runningStateChangeFile;

    private static DomainTestSupport testSupport;

    private static void initializeModule(final DomainTestSupport support) throws IOException {
        // Get module.xml, create modules.jar and add to test config
        final InputStream moduleXml = getModuleXml("process-state-listener-module.xml");
        StreamExporter exporter = createResourceRoot(TestListener.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("process-state-listener.jar", exporter);
        support.addTestModule(TestListener.class.getPackage().getName(), moduleXml, content);
    }

    private static InputStream getModuleXml(final String name) {
        // Get the module xml
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return tccl.getResourceAsStream("extension/" + name);
    }

    private static StreamExporter createResourceRoot(Package... additionalPackages) {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        if (additionalPackages != null) {
            for (Package pkg : additionalPackages) {
                archive.addPackage(pkg);
            }
        }
        return archive.as(ZipExporter.class);
    }

    private static ModelNode executeForResult(final ModelNode op) throws RuntimeException {
        return testSupport.getDomainMasterLifecycleUtil().executeForResult(op);
    }

    @BeforeClass
    public static void setup() throws Exception {
        data = Files.createTempDirectory("notifications");
        runtimeConfigurationStateChangeFile = data.resolve(TestListener.DEFAULT_RUNTIME_CONFIGURATION_STATE_CHANGE_FILENAME);
        runningStateChangeFile = data.resolve(TestListener.DEFAULT_RUNNING_STATE_CHANGE_FILENAME);

        // create domain test support
        testSupport = DomainTestSupport.create(DomainTestSupport.Configuration.create(ProcessStateListenerTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", null));
        // add module
        initializeModule(testSupport);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        testSupport.close();
        PathUtil.deleteSilentlyRecursively(data);
    }

    @Before
    public void clearNotificationFiles() throws Exception {
        Files.deleteIfExists(runtimeConfigurationStateChangeFile);
        Files.createFile(runtimeConfigurationStateChangeFile);
        Files.deleteIfExists(runningStateChangeFile);
        Files.createFile(runningStateChangeFile);
    }

    /**
     * Add a process-state-listener on the master host by invoking a management operation. The listener will write a
     * line in a file for every host controller state changes.
     */
    @Test
    public void testListenerOnHostController() throws Throwable {
        PathAddress address = testSupport.getDomainMasterLifecycleUtil().getAddress()
                .append("subsystem", "core-management")
                .append("process-state-listener", "my-listener");
        startMasterHost();
        try {
            addListener(address, TestListener.class.getPackage().getName(), null, null);

            performStateChanges();

            RuntimeConfigurationStateChanges runtimeConfigChanges = new RuntimeConfigurationStateChanges(runtimeConfigurationStateChangeFile);
            // reload to admin only
            runtimeConfigChanges.add(HOST_CONTROLLER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
            runtimeConfigChanges.add(HOST_CONTROLLER, ADMIN_ONLY, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
            // reload to normal
            runtimeConfigChanges.add(HOST_CONTROLLER, ADMIN_ONLY, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
            runtimeConfigChanges.add(HOST_CONTROLLER, NORMAL, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
            // stop
            runtimeConfigChanges.add(HOST_CONTROLLER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);

            runtimeConfigChanges.verify();

            RunningStateChanges runningStateChanges = new RunningStateChanges(runningStateChangeFile);
            // reload to admin only
            runningStateChanges.add(HOST_CONTROLLER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.STOPPING);
            runningStateChanges.add(HOST_CONTROLLER, ADMIN_ONLY, Process.RunningState.STARTING, Process.RunningState.ADMIN_ONLY);
            // reload to normal
            runningStateChanges.add(HOST_CONTROLLER, ADMIN_ONLY, Process.RunningState.ADMIN_ONLY, Process.RunningState.STOPPING);
            runningStateChanges.add(HOST_CONTROLLER, NORMAL, Process.RunningState.STARTING, Process.RunningState.NORMAL);
            // restart servers
            // suspend servers
            // resume servers
            // stop
            runningStateChanges.add(HOST_CONTROLLER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.STOPPING);

            runningStateChanges.verify();
        } finally {
            stopMasterHost();
        }
    }

    @Test
    public void testListenerOnDomainServer() throws Throwable {
        PathAddress address = PathAddress.pathAddress("profile", "default")
                .append("subsystem", "core-management")
                .append("process-state-listener", "my-listener");
        startMasterHost();
        try {
            addListener(address, TestListener.class.getPackage().getName(), null, null);

            performStateChanges();

            RuntimeConfigurationStateChanges runtimeConfigChanges = new RuntimeConfigurationStateChanges(runtimeConfigurationStateChangeFile);
            // reload to admin only
            runtimeConfigChanges.add(DOMAIN_SERVER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
            runtimeConfigChanges.add(DOMAIN_SERVER, NORMAL, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
            // reload to normal
            runtimeConfigChanges.add(DOMAIN_SERVER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);
            runtimeConfigChanges.add(DOMAIN_SERVER, NORMAL, Process.RuntimeConfigurationState.STARTING, Process.RuntimeConfigurationState.RUNNING);
            // stop
            runtimeConfigChanges.add(DOMAIN_SERVER, NORMAL, Process.RuntimeConfigurationState.RUNNING, Process.RuntimeConfigurationState.STOPPING);

            runtimeConfigChanges.verify();

            RunningStateChanges runningStateChanges = new RunningStateChanges(runningStateChangeFile);
            // reload to admin only
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.SUSPENDING);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.STOPPING);
            // reload to normal
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.NORMAL);

            // restart servers
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.SUSPENDING);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.STOPPING);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.NORMAL);
            // suspend servers
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.SUSPENDING);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
            // resume servers
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.NORMAL);
            // stop
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.NORMAL, Process.RunningState.SUSPENDING);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDING, Process.RunningState.SUSPENDED);
            runningStateChanges.add(DOMAIN_SERVER, NORMAL, Process.RunningState.SUSPENDED, Process.RunningState.STOPPING);

            runningStateChanges.verify();
        } finally {
            stopMasterHost();
        }
    }

    private void performStateChanges() throws Exception {
        reloadMasterHost(true);
        reloadMasterHost(false);
        restartMainServerGroup();
        suspendMainServerGroup();
        resumeMainServerGroup();
        shutdownMasterHost();
    }

    private static void addListener(PathAddress address, String module, Properties properties, Integer timeout) {
        ModelNode addListener = Util.createAddOperation(address);
        addListener.get("class").set(TestListener.class.getName());
        addListener.get("module").set(module);
        ModelNode props = new ModelNode();
        props.add("file", data.toAbsolutePath().toString());
        if (properties != null && !properties.isEmpty()) {
            for (String name : properties.stringPropertyNames()) {
                props.add(name, properties.getProperty(name));
            }
        }
        addListener.get("properties").set(props);
        if (timeout != null)
            addListener.get("timeout").set(timeout);

        executeForResult(addListener);
    }

    private void reloadMasterHost(boolean adminOnly) throws Exception {
        ModelNode reload = Util.createEmptyOperation("reload", testSupport.getDomainMasterLifecycleUtil().getAddress());
        if (adminOnly)
            reload.get("admin-only").set(true);
        testSupport.getDomainMasterLifecycleUtil().executeAwaitConnectionClosed(reload);
        testSupport.getDomainMasterLifecycleUtil().connect();
        if (adminOnly) {
            testSupport.getDomainMasterLifecycleUtil().awaitHostController(System.currentTimeMillis());
        } else {
            testSupport.getDomainMasterLifecycleUtil().awaitServers(System.currentTimeMillis());
        }
    }

    private void shutdownMasterHost() throws Exception {
        ModelNode shutdown = Util.createEmptyOperation("shutdown", testSupport.getDomainMasterLifecycleUtil().getAddress());
        testSupport.getDomainMasterLifecycleUtil().executeAwaitConnectionClosed(shutdown);
    }

    private void startMasterHost() {
        testSupport.getDomainMasterLifecycleUtil().start();
    }

    private void stopMasterHost() {
        testSupport.getDomainMasterLifecycleUtil().stop();
    }

    private void restartMainServerGroup() throws TimeoutException, InterruptedException {
        ModelNode suspend = Util.createEmptyOperation("restart-servers", MAIN_SERVER_GROUP_ADDRESS);
        executeForResult(suspend);
        testSupport.getDomainMasterLifecycleUtil().awaitServers(System.currentTimeMillis());
    }

    private void suspendMainServerGroup() {
        ModelNode suspend = Util.createEmptyOperation("suspend-servers", MAIN_SERVER_GROUP_ADDRESS);
        executeForResult(suspend);
    }

    private void resumeMainServerGroup() throws Exception {
        ModelNode resume = Util.createEmptyOperation("resume-servers", MAIN_SERVER_GROUP_ADDRESS);
        executeForResult(resume);
        testSupport.getDomainMasterLifecycleUtil().awaitServers(System.currentTimeMillis());
    }

    private abstract static class StateChanges {

        final List<String> changes;
        private final Path file;

        public StateChanges(Path file) {
            this.file = file;
            this.changes = new ArrayList<>();
        }

        public void verify() throws IOException {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            //MatcherAssert.assertThat(lines, Is.is(changes));
            for(int i = 0; i <lines.size(); i++) {
                MatcherAssert.assertThat("Incorrect match at line " + i + " " + lines.get(i), lines.get(i), is(changes.get(i)));
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
