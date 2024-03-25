/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.jboss.as.server.ServerEnvironment.HOME_DIR;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Properties;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.version.ProductConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * Simple test for UUID generation.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ServerEnvironmentTestCase {

    private static final Path homeDir = new File(System.getProperty("basedir", ".")).toPath().resolve("target").resolve("wildlfy");

    @Before
    public void clean() throws IOException {
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

    @Test
    public void testUUIDLifeCycle() throws IOException {
        ProductConfig productConfig = new ProductConfig(null, null, null);
        Properties props = new Properties();
        Path standaloneDir = homeDir.resolve("standalone");
        Files.createDirectories(standaloneDir.resolve("configuration"));
        Files.createFile(standaloneDir.resolve("configuration").resolve("standalone.xml"));
        Path uuidPath = standaloneDir.resolve("data").resolve("kernel").resolve("process-uuid");
        assertThat(Files.notExists(uuidPath), is(true));
        props.put(HOME_DIR, homeDir.toAbsolutePath().toString());
        //Check creation on startup
        ServerEnvironment serverEnvironment = new ServerEnvironment(null, props, System.getenv(), "standalone.xml",
                ConfigurationFile.InteractionPolicy.READ_ONLY, ServerEnvironment.LaunchType.STANDALONE, RunningMode.NORMAL, productConfig, false);
        assertThat(Files.exists(uuidPath), is(true));
        List<String> uuids = Files.readAllLines(uuidPath);
        assertThat(uuids, is(not(nullValue())));
        assertThat(uuids.size(), is(1));
        String uuid = uuids.get(0);
        //Check nothing happens on startup if file is already there
        serverEnvironment = new ServerEnvironment(null, props, System.getenv(), "standalone.xml",
                ConfigurationFile.InteractionPolicy.READ_ONLY, ServerEnvironment.LaunchType.STANDALONE, RunningMode.NORMAL, productConfig, false);
        assertThat(Files.exists(uuidPath), is(true));
        uuids = Files.readAllLines(uuidPath);
        assertThat(uuids, is(not(nullValue())));
        assertThat(uuids.size(), is(1));
        assertThat(uuids.get(0), is(uuid));
        //Check re-creation on startup
        Files.delete(uuidPath);
        assertThat(Files.notExists(uuidPath), is(true));
        serverEnvironment = new ServerEnvironment(null, props, System.getenv(), "standalone.xml",
                ConfigurationFile.InteractionPolicy.READ_ONLY, ServerEnvironment.LaunchType.STANDALONE, RunningMode.NORMAL, productConfig, false);
        assertThat(Files.exists(uuidPath), is(true));
        uuids = Files.readAllLines(uuidPath);
        assertThat(uuids, is(not(nullValue())));
        assertThat(uuids.size(), is(1));
        assertThat(uuids.get(0), is(not(uuid)));
        Files.delete(uuidPath);
    }

    @Test
    public void testAliasFunctionality() throws IOException {
        Properties props = new Properties();
        Path standaloneDir = homeDir.resolve("standalone");
        Files.createDirectories(standaloneDir.resolve("configuration"));
        Files.createFile(standaloneDir.resolve("configuration").resolve("standalone.xml"));
        Files.createFile(standaloneDir.resolve("configuration").resolve("standalone-load-balancer.xml"));
        Files.createFile(standaloneDir.resolve("configuration").resolve("custom.xml"));
        props.put(HOME_DIR, homeDir.toAbsolutePath().toString());

        // default stability = COMMUNITY
        ProductConfig productConfig = ProductConfig.fromFilesystemSlot(null, "", props);

        ServerEnvironment serverEnvironment = createServerEnvironment(props, null, productConfig);
        assertThat(serverEnvironment.getServerConfigurationFile().getBootFile().getName(), is("standalone.xml"));

        serverEnvironment = createServerEnvironment(props, "lb", productConfig);
        assertThat(serverEnvironment.getServerConfigurationFile().getBootFile().getName(), is("standalone-load-balancer.xml"));

        serverEnvironment = createServerEnvironment(props, "custom.xml", productConfig);
        assertThat(serverEnvironment.getServerConfigurationFile().getBootFile().getName(), is("custom.xml"));
    }

    @Test(expected = IllegalStateException.class)
    public void testAliasNotWorkingInDefaultStability() {
        Properties props = new Properties();
        props.put(HOME_DIR, homeDir.toAbsolutePath().toString());

        // default stability = DEFAULT
        ProductConfig productConfig = new ProductConfig(null, null, null);
        createServerEnvironment(props, "lb", productConfig);
    }

    private ServerEnvironment createServerEnvironment(Properties props, String serverConfig, ProductConfig productConfig) {
        return new ServerEnvironment(null, props, System.getenv(), serverConfig,
                ConfigurationFile.InteractionPolicy.READ_ONLY, ServerEnvironment.LaunchType.STANDALONE, RunningMode.NORMAL, productConfig, false);
    }
}
