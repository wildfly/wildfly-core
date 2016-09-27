/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.server;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.jboss.as.server.ServerEnvironment.HOME_DIR;
import static org.junit.Assert.assertThat;

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
        Properties props = new Properties();
        Path standaloneDir = homeDir.resolve("standalone");
        Files.createDirectories(standaloneDir.resolve("configuration"));
        Files.createFile(standaloneDir.resolve("configuration").resolve("standalone.xml"));
        Path uuidPath = standaloneDir.resolve("data").resolve("kernel").resolve("process-uuid");
        assertThat(Files.notExists(uuidPath), is(true));
        props.put(HOME_DIR, homeDir.toAbsolutePath().toString());
        //Check creation on startup
        ServerEnvironment serverEnvironment = new ServerEnvironment(null, props, System.getenv(), "standalone.xml",
                ConfigurationFile.InteractionPolicy.READ_ONLY, ServerEnvironment.LaunchType.STANDALONE, RunningMode.NORMAL, null, false);
        assertThat(Files.exists(uuidPath), is(true));
        List<String> uuids = Files.readAllLines(uuidPath);
        assertThat(uuids, is(not(nullValue())));
        assertThat(uuids.size(), is(1));
        String uuid = uuids.get(0);
        //Check nothing happens on startup if file is already there
        serverEnvironment = new ServerEnvironment(null, props, System.getenv(), "standalone.xml",
                ConfigurationFile.InteractionPolicy.READ_ONLY, ServerEnvironment.LaunchType.STANDALONE, RunningMode.NORMAL, null, false);
        assertThat(Files.exists(uuidPath), is(true));
        uuids = Files.readAllLines(uuidPath);
        assertThat(uuids, is(not(nullValue())));
        assertThat(uuids.size(), is(1));
        assertThat(uuids.get(0), is(uuid));
        //Check re-creation on startup
        Files.delete(uuidPath);
        assertThat(Files.notExists(uuidPath), is(true));
        serverEnvironment = new ServerEnvironment(null, props, System.getenv(), "standalone.xml",
                ConfigurationFile.InteractionPolicy.READ_ONLY, ServerEnvironment.LaunchType.STANDALONE, RunningMode.NORMAL, null, false);
        assertThat(Files.exists(uuidPath), is(true));
        uuids = Files.readAllLines(uuidPath);
        assertThat(uuids, is(not(nullValue())));
        assertThat(uuids.size(), is(1));
        assertThat(uuids.get(0), is(not(uuid)));
        Files.delete(uuidPath);
    }
}
