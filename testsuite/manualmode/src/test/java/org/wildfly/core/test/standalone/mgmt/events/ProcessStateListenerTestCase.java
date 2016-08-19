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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.events.provider.TestListener;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ProcessStateListenerTestCase {

    @Inject
    protected static ServerController controller;

    private TestModule module;

    private static TestModule createModule() throws IOException, URISyntaxException {
        // Get module.xml, create modules.jar and add to test config
        final File moduleXml = getModuleXml("process-state-listener-module.xml");
        TestModule module = new TestModule(TestListener.class.getPackage().getName(), moduleXml);
        module.addResource("process-state-listener.jar").addPackage(TestListener.class.getPackage());
        module.create(true);
        return module;
    }

    static File getModuleXml(final String name) throws URISyntaxException {
        // Get the module xml
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return new File(tccl.getResource("extension/" + name).toURI());
    }

    private static ModelNode executeForResult(final ModelNode op, final ModelControllerClient modelControllerClient) throws IOException, MgmtOperationException {
        try {
            return DomainTestUtils.executeForResult(op, modelControllerClient);
        } catch (MgmtOperationException e) {
            System.out.println(" Op failed:");
            System.out.println(e.getOperation());
            System.out.println("with result");
            System.out.println(e.getResult());
            throw e;
        }
    }

    @Before
    public void setup() throws Exception {
        module = createModule();
    }

    @After
    public void tearDown() throws Exception {
        module.remove();
    }

    /**
     *
     * Add a process-state-listener on the master host by invoking a management operation. The listener will write a
     * line in a file for every host controller state changes.
     */
    @Test
    public void testListenerOnStandaloneServer() throws Throwable {
        PathAddress address = PathAddress.EMPTY_ADDRESS
                .append("subsystem", "core-management")
                .append("process-state-listener", "my-listener");
        doTestListenerServer(address, ProcessType.STANDALONE_SERVER);
    }

    private void doTestListenerServer(PathAddress address, ProcessType processType) throws Throwable {
        controller.start();
        Path tempFile = Files.createTempDirectory("notifications");
        Path runningStatePath = tempFile.resolve("runningState.txt");
        Path runtimeConfigStatePath = tempFile.resolve("runtimeConfigurationState.txt");
        try {
            ModelNode addListener = Util.createAddOperation(address);
            addListener.get("class").set(TestListener.class.getName());
            addListener.get("module").set(TestListener.class.getPackage().getName());
            ModelNode props = new ModelNode();
            props.add("file", tempFile.toFile().getAbsolutePath());
            addListener.get("properties").set(props);
            executeForResult(addListener, controller.getClient().getControllerClient());

            controller.reload();
            // => changes state twice: running -> stopping & starting -> running
            controller.stop();
            List<String> expectedRutimeConfigLines = new ArrayList<>(9);
            List<String> expectedRunningLines = new ArrayList<>(9);
            // state changed after invoking reload
            expectedRutimeConfigLines.add(processType + " normal ok stopping");
            expectedRunningLines.add(processType + " normal normal stopping");
            // state changed after server is reloading
            expectedRunningLines.add(processType + " normal suspended normal");
            expectedRutimeConfigLines.add(processType + " normal starting ok");
            //shutdown is starting by suspending the server
            expectedRunningLines.add(processType + " normal normal suspending");
            // state changed after server is suspended
            expectedRunningLines.add(processType + " normal suspending suspended");
            // state changed after server is shutdown
            expectedRutimeConfigLines.add(processType + " normal ok stopping");
            expectedRunningLines.add(processType + " normal suspended stopping");
            // => changes state : running -> stopping
            checkLines(runtimeConfigStatePath.toFile(),expectedRutimeConfigLines.toArray(new String[expectedRutimeConfigLines.size()]));
            checkLines(runningStatePath.toFile(),expectedRunningLines.toArray(new String[expectedRunningLines.size()]));
        } finally {
            try {
                controller.start();
                controller.getClient().getControllerClient().execute(Util.createRemoveOperation(address));
            } finally {
                controller.stop();
            }
            Files.deleteIfExists(runtimeConfigStatePath);
            Files.deleteIfExists(runningStatePath);
        }
    }

    public static void checkLines(File file, String... expectedLines) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        assertEquals("Expected lines: " + Arrays.toString(expectedLines) + " but was " + Arrays.toString(lines.toArray()), expectedLines.length, lines.size());
        for (String expectedLine : expectedLines) {
            Assert.assertTrue("Incorrect line " + expectedLine, lines.remove(expectedLine));
        }
        Assert.assertTrue(lines.isEmpty());
    }
}
