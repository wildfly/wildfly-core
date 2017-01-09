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


import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.test.events.provider.TestListener;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public class ProcessStateListenerTestCase {

    private DomainTestSupport testSupport;

    public static void initializeModule(final DomainTestSupport support) throws IOException {
        // Get module.xml, create modules.jar and add to test config
        final InputStream moduleXml = getModuleXml("process-state-listener-module.xml");
        StreamExporter exporter = createResourceRoot(TestListener.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("process-state-listener.jar", exporter);
        support.addTestModule(TestListener.class.getPackage().getName(), moduleXml, content);
    }

    static InputStream getModuleXml(final String name) {
        // Get the module xml
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return tccl.getResourceAsStream("extension/" + name);
    }

    static StreamExporter createResourceRoot(Package... additionalPackages) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        if (additionalPackages != null) {
            for (Package pkg : additionalPackages) {
                archive.addPackage(pkg);
            }
        }
        return archive.as(ZipExporter.class);
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
    public void setupDomain() throws Exception {
        testSupport = DomainTestSupport.create(DomainTestSupport.Configuration.create(ProcessStateListenerTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-master.xml", null));
        initializeModule(testSupport);
    }

    /**
     *
     * Add a process-state-listener on the master host by invoking a management operation. The listener will write a
     * line in a file for every host controller state changes.
     */
    @Test
    public void testListenerOnHostController() throws Throwable {
        DomainLifecycleUtil domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        PathAddress address = domainMasterLifecycleUtil.getAddress()
                .append("subsystem", "core-management")
                .append("process-state-listener", "my-listener");
        doTestListenerServer(address, ProcessType.HOST_CONTROLLER);
    }

    @Test
    public void testListenerOnDomainServer() throws Throwable {
        PathAddress address = PathAddress.pathAddress("profile", "default")
                .append("subsystem", "core-management")
                .append("process-state-listener", "my-listener");
        doTestListenerServer(address, ProcessType.DOMAIN_SERVER);
    }

    private void doTestListenerServer(PathAddress address, ProcessType processType) throws Throwable {
        testSupport.start();
        Path tempFile = Files.createTempDirectory("notifications");
        Path runningStatePath = tempFile.resolve("runningState.txt");
        Path runtimeConfigStatePath = tempFile.resolve("runtimeConfigurationState.txt");
        try {
            DomainLifecycleUtil domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
            ModelNode addListener = Util.createAddOperation(address);
            addListener.get("class").set(TestListener.class.getName());
            addListener.get("module").set(TestListener.class.getPackage().getName());
            ModelNode props = new ModelNode();
            props.add("file", tempFile.toFile().getAbsolutePath());
            addListener.get("properties").set(props);
            executeForResult(addListener, domainMasterLifecycleUtil.getDomainClient());

            reload(testSupport);
            // => changes state twice: running -> stopping & starting -> running
            if(processType == ProcessType.DOMAIN_SERVER) {
                PathAddress serverGroupAddress = PathAddress.pathAddress("server-group", "main-server-group");
                ModelNode suspend = Util.createEmptyOperation("suspend-servers", serverGroupAddress);
                executeForResult(suspend, domainMasterLifecycleUtil.getDomainClient());
                ModelNode resume = Util.createEmptyOperation("resume-servers", serverGroupAddress);
                executeForResult(resume, domainMasterLifecycleUtil.getDomainClient());
            }
            // => changes state twice: running -> suspended -> running
            ModelNode shutdown = Util.createEmptyOperation("shutdown", domainMasterLifecycleUtil.getAddress());
            testSupport.getDomainMasterLifecycleUtil().executeAwaitConnectionClosed(shutdown);
            // => changes state : running -> stopping
            testSupport.stop();
            List<String> expectedRutimeConfigLines = new ArrayList<>(9);
            List<String> expectedRunningLines = new ArrayList<>(9);
            // state changed after invoking reload
            expectedRutimeConfigLines.add(processType + " normal ok stopping");
            expectedRunningLines.add(processType + " normal normal stopping");
            // state changed after server is reloading
            if (processType == ProcessType.DOMAIN_SERVER) {
                expectedRunningLines.add(processType + " normal starting suspended");
                expectedRunningLines.add(processType + " normal suspended normal");
                expectedRutimeConfigLines.add(processType + " normal starting ok");
            } else {
                expectedRutimeConfigLines.add(processType + " normal starting ok");
                expectedRunningLines.add(processType + " normal starting normal");
            }
            if (processType == ProcessType.DOMAIN_SERVER) {
                expectedRunningLines.add(processType + " normal normal suspending");
                // state changed after server is suspended
                expectedRunningLines.add(processType + " normal suspending suspended");
                // state changed after server is resumed
                expectedRunningLines.add(processType + " normal suspended normal");
            }
            // state changed after server is shutdown
            expectedRutimeConfigLines.add(processType + " normal ok stopping");
            expectedRunningLines.add(processType + " normal normal stopping");

            checkLines(runtimeConfigStatePath.toFile(),expectedRutimeConfigLines.toArray(new String[expectedRutimeConfigLines.size()]));
            checkLines(runningStatePath.toFile(),expectedRunningLines.toArray(new String[expectedRunningLines.size()]));
        } finally {
            try {
                testSupport = DomainTestSupport.create(DomainTestSupport.Configuration.create(ProcessStateListenerTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-master.xml", null));
                testSupport.start();
                testSupport.getDomainMasterLifecycleUtil().getDomainClient().execute(Util.createRemoveOperation(address));
            } catch (IOException ex) {
            } finally {
                testSupport.stop();
            }
            Files.deleteIfExists(runtimeConfigStatePath);
            Files.deleteIfExists(runningStatePath);
        }
    }

    public static void checkLines(File file, String... expectedLines) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        assertEquals("Expected lines: " + Arrays.toString(expectedLines) + " but was " + Arrays.toString(lines.toArray()), expectedLines.length, lines.size());
        for (String expectedLine : expectedLines) {
            Assert.assertTrue("Incorrect line " + expectedLine + " not found in " + Arrays.toString(lines.toArray()), lines.remove(expectedLine));
        }
        Assert.assertTrue(lines.isEmpty());
    }

    private void reload(DomainTestSupport testSupport) throws Exception {
        ModelNode reload = Util.createEmptyOperation("reload", testSupport.getDomainMasterLifecycleUtil().getAddress());
        testSupport.getDomainMasterLifecycleUtil().executeAwaitConnectionClosed(reload);
        testSupport.getDomainMasterLifecycleUtil().connect();
        testSupport.getDomainMasterLifecycleUtil().awaitServers(System.currentTimeMillis());
    }
}