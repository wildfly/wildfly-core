/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.host.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.host.controller.HostControllerEnvironment.HOME_DIR;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.RunningMode;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class ManagedServerBootCmdFactoryTestCase {

    private static final Path homeDir = new File(System.getProperty("basedir", ".")).toPath().resolve("target").resolve("wildlfy");
    private static final Path domainDir = homeDir.resolve("domain");
    private static final Path configurationDir = domainDir.resolve("configuration");

    @BeforeClass
    public static void createHomeDir() throws IOException {
        Files.createDirectories(configurationDir);
    }

    @AfterClass
    public static void clean() throws IOException {
        if (Files.exists(homeDir)) {
            Files.walkFileTree(homeDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

            });
        }
    }

    private static HostControllerEnvironment getTestHostEnvironment() throws UnknownHostException {
        Map<String, String> hostSystemProperties = new HashMap<>();
        hostSystemProperties.put("jboss.server.log.dir", "/tmp/");
        hostSystemProperties.put("jboss.domain.log.dir", "/tmp/");
        hostSystemProperties.put("jboss.server.temp.dir", "/tmp/");
        hostSystemProperties.put(HOME_DIR, homeDir.toAbsolutePath().toString());
        return new HostControllerEnvironment(
                hostSystemProperties, false, "/opt/wildfly/modules",
                InetAddress.getLocalHost(), 8080, InetAddress.getLocalHost(), 9990, null, null, null, null, null,
                RunningMode.NORMAL, true, true, null);
    }

    private static ModelNode getHostModel() {
        ModelNode hostModel = new ModelNode();
        ModelNode serverModel = new ModelNode();
        serverModel.get("group").set("test-group");
        final ModelNode jvm = serverModel.get("jvm", "test-jvm");
        jvm.get("heap-size").set("64m");
        jvm.get("max-heap-size").set("256m");
        jvm.get("module-options").setEmptyList().add("-javaagent:test-agent.jar");
        hostModel.get("server-config").get("test-server").set(serverModel);
        return hostModel;
    }

    private static ModelNode getDomainModel() {
        ModelNode domainModel = new ModelNode();
        domainModel.get("server-group").get("test-group").setEmptyObject();
        return domainModel;
    }

    /**
     * Test of createConfiguration method, of class ManagedServerBootCmdFactory.
     */
    @Test
    public void testCreateConfiguration() throws UnknownHostException {
        System.out.println("createConfiguration");
        ManagedServerBootCmdFactory instance = new ManagedServerBootCmdFactory("test-server", getDomainModel(), getHostModel(), getTestHostEnvironment(), ExpressionResolver.TEST_RESOLVER, false);
        ManagedServerBootConfiguration result = instance.createConfiguration();
        Assert.assertNotNull(result);
    }

    /**
     * Test of getServerLaunchCommand method, of class
     * ManagedServerBootCmdFactory.
     */
    @Test
    public void testGetServerLaunchCommand() throws UnknownHostException {
        System.out.println("getServerLaunchCommand");
        ManagedServerBootCmdFactory instance = new ManagedServerBootCmdFactory("test-server", getDomainModel(), getHostModel(), getTestHostEnvironment(), ExpressionResolver.TEST_RESOLVER, false);
        List<String> result = instance.getServerLaunchCommand();
        Assert.assertThat(result.size(), is(notNullValue()));
        if (result.size() > 18) {
            Assert.assertThat(result.size(), is(21));
        } else {
            Assert.assertThat(result.size(), is(18));
        }
        Assert.assertTrue("Missing -javaagent:test-agent.jar entry: " + result, result.contains("-javaagent:test-agent.jar"));
        boolean sawDServer = false;
        boolean sawDpcid = false;
        boolean sawJbmAgent = false;
        for (String arg : result) {
            if (arg.startsWith("-Djboss.server.log.dir")) {
                Assert.assertThat(arg, is(not("-Djboss.server.log.dir=/tmp/")));
            } else if (arg.startsWith("-Djboss.server.temp.dir")) {
                Assert.assertThat(arg, is(not("-Djboss.server.temp.dir=/tmp/")));
            } else if (arg.startsWith("-Djboss.domain.log.dir")) {
                Assert.assertThat(arg, is("-Djboss.domain.log.dir=/tmp/"));
            } else if (arg.equals("-D[" + ManagedServer.getServerProcessName("test-server") + "]")) {
                sawDServer = true;
            } else if (arg.startsWith("-D[pcid:") && arg.endsWith("]")) {
                sawDpcid = true;
            } else if (arg.startsWith("-javaagent:") && arg.endsWith("jboss-modules.jar")) {
                sawJbmAgent = true;
            }
        }
        Assert.assertTrue(sawDServer);
        Assert.assertTrue(sawDpcid);
        Assert.assertTrue("Missing jboss-modules.jar configured as an agent: " + result, sawJbmAgent);
    }
}
