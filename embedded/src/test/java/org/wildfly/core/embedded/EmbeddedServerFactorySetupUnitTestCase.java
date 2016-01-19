/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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
package org.wildfly.core.embedded;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.jboss.as.server.ServerEnvironment;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class EmbeddedServerFactorySetupUnitTestCase {
    final Path standardJBossHome;
    final Path alternativeServer;
    final Path alternativeDataDir;
    final Path alternativeConfigDir;

    Path embeddedRoot;

    public EmbeddedServerFactorySetupUnitTestCase() {
        try {
            standardJBossHome = createStandardAsHome();
            alternativeServer = createServer(createRootDir(), "standalone2", 2);
            alternativeDataDir = createDataOrConfigDir(createRootDir(), "otherData", 3);
            alternativeConfigDir = createDataOrConfigDir(createRootDir(), "otherConfig", 4);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Before
    public void createEmbeddedRoot() throws IOException {
        embeddedRoot = Paths.get("target/embedded-root").toAbsolutePath();
        if(Files.exists(embeddedRoot)){
            deleteDirectory(embeddedRoot.toFile());
        }
        Files.createDirectories(embeddedRoot);
        Assert.assertTrue(Files.exists(embeddedRoot));
    }

    @Test
    public void testNoSpecialConfig() throws Exception {
        Properties props = new Properties();
        EmbeddedStandaloneServerFactory.setupCleanDirectories(standardJBossHome, props);
        Assert.assertEquals(0, props.size());
    }

    @Test
    public void testEmbeddedRootNoOverrides() throws Exception {
        Properties props = new Properties();
        props.setProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT, embeddedRoot.toAbsolutePath().toString());
        EmbeddedStandaloneServerFactory.setupCleanDirectories(standardJBossHome, props);

        Assert.assertEquals(4, props.size());
        Assert.assertEquals(embeddedRoot.toAbsolutePath().toString(), props.getProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT));
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_BASE_DIR, -1);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_DATA_DIR, 1);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_CONFIG_DIR, 1);
    }

    @Test
    public void testEmbeddedRootServerOverride() throws Exception {
        Properties props = new Properties();
        props.setProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT, embeddedRoot.toAbsolutePath().toString());
        props.setProperty(ServerEnvironment.SERVER_BASE_DIR, alternativeServer.toAbsolutePath().toString());
        EmbeddedStandaloneServerFactory.setupCleanDirectories(standardJBossHome, props);
        Assert.assertEquals(4, props.size());
        Assert.assertEquals(embeddedRoot.toAbsolutePath().toString(), props.getProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT));
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_BASE_DIR, -1);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_DATA_DIR, 2);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_CONFIG_DIR, 2);
    }

    @Test
    public void testDataAndConfigOverride() throws Exception {
        Properties props = new Properties();
        props.setProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT, embeddedRoot.toAbsolutePath().toString());
        props.setProperty(ServerEnvironment.SERVER_DATA_DIR, alternativeDataDir.toAbsolutePath().toString());
        props.setProperty(ServerEnvironment.SERVER_CONFIG_DIR, alternativeConfigDir.toAbsolutePath().toString());
        EmbeddedStandaloneServerFactory.setupCleanDirectories(standardJBossHome, props);
        Assert.assertEquals(4, props.size());
        Assert.assertEquals(embeddedRoot.toAbsolutePath().toString(), props.getProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT));
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_BASE_DIR, -1);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_DATA_DIR, 3);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_CONFIG_DIR, 4);
    }

    @Test
    public void testServerOverrideAndDataAndConfigOverride() throws Exception {
        Properties props = new Properties();
        props.setProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT, embeddedRoot.toAbsolutePath().toString());
        props.setProperty(ServerEnvironment.SERVER_BASE_DIR, alternativeServer.toAbsolutePath().toString());
        props.setProperty(ServerEnvironment.SERVER_DATA_DIR, alternativeDataDir.toAbsolutePath().toString());
        props.setProperty(ServerEnvironment.SERVER_CONFIG_DIR, alternativeConfigDir.toAbsolutePath().toString());
        EmbeddedStandaloneServerFactory.setupCleanDirectories(standardJBossHome, props);
        Assert.assertEquals(4, props.size());
        Assert.assertEquals(embeddedRoot.toAbsolutePath().toString(), props.getProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT));
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_BASE_DIR, -1);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_DATA_DIR, 3);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_CONFIG_DIR, 4);
    }

    @Test
    public void testServerOverrideAndConfigOverride() throws Exception {
        Properties props = new Properties();
        props.setProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT, embeddedRoot.toAbsolutePath().toString());
        props.setProperty(ServerEnvironment.SERVER_BASE_DIR, alternativeServer.toAbsolutePath().toString());
        props.setProperty(ServerEnvironment.SERVER_CONFIG_DIR, alternativeConfigDir.toAbsolutePath().toString());
        EmbeddedStandaloneServerFactory.setupCleanDirectories(standardJBossHome, props);
        Assert.assertEquals(4, props.size());
        Assert.assertEquals(embeddedRoot.toAbsolutePath().toString(), props.getProperty(EmbeddedStandaloneServerFactory.JBOSS_EMBEDDED_ROOT));
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_BASE_DIR, -1);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_DATA_DIR, 2);
        assertPropertyAndEmbeddedRootFile(props, ServerEnvironment.SERVER_CONFIG_DIR, 4);
    }

    private void assertPropertyAndEmbeddedRootFile(Properties props, String property, int id) {
        String dirName = props.getProperty(property);
        Assert.assertNotNull(dirName);
        File dir = new File(dirName);
        Assert.assertTrue(dir.exists());
        Assert.assertTrue(dir.isDirectory());
        File expected = id >= 0 ? new File(dir, String.valueOf(id)) : dir;
        Assert.assertTrue(expected.exists());


        File parent = dir.getParentFile();
        while (parent != null) {
            if (parent.equals(embeddedRoot.toFile())) {
                return;
            }
            parent = parent.getParentFile();
        }
        Assert.fail(dir + " is not a child of " + embeddedRoot);
    }


    private Path createRootDir() throws IOException {
        Path root = Paths.get("target/server-home");
        if (Files.notExists(root)){
            Files.createDirectory(root);
        }
        return root;
    }

    private Path createStandardAsHome() throws IOException {
        Path home = createRootDir().resolve("jboss-home");
        if (Files.notExists(home)) {
            Files.createDirectory(home);
        }

        createServer(home, "standalone", 1);

        return home;
    }

    private Path createServer(Path home, String serverName, int id) throws IOException {
        Path server = home.resolve(serverName);

        if (Files.notExists(server)) {
            Files.createDirectory(server);
        }

        createDataOrConfigDir(server, "data", id);
        createDataOrConfigDir(server, "configuration", id);
        return server;
    }

    private Path createDataOrConfigDir(Path server, String name, int id) {
        Path dir = server.resolve(name);

        Path file = dir.resolve(String.valueOf(id));// new File(dir, String.valueOf(id));
        if (Files.notExists(file)) {
            try {
                Files.createDirectories(dir);
                Files.createFile(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Assert.assertTrue(Files.exists(file));

        return dir;
    }

    private void deleteDirectory(File dir) {
        for (String name : dir.list()) {
            File current = new File(dir, name);
            if (current.isDirectory()) {
                deleteDirectory(current);
            } else {
                Assert.assertTrue(current.delete());
            }
        }
    }
}
