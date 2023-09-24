/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.jboss.as.host.controller.HostControllerEnvironment.HOME_DIR;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.as.controller.RunningMode;
import org.junit.Before;
import org.junit.Test;

/**
 * Simple test for UUID generation.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class HostControllerEnvironmentTestCase {

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
        Map<String, String> hostProperties = new HashMap();
        Path domainDir = homeDir.resolve("domain");
        Files.createDirectories(domainDir.resolve("configuration"));
        Path uuidPath = domainDir.resolve("data").resolve("kernel").resolve("process-uuid");
        assertThat(Files.notExists(uuidPath), is(true));
        hostProperties.put(HOME_DIR, homeDir.toAbsolutePath().toString());
        //Check creation on startup
        HostControllerEnvironment hcEnvironment = new HostControllerEnvironment(hostProperties, false, "",
                InetAddress.getLocalHost(), 8080, InetAddress.getLocalHost(), 9990, null, null, null, null, null,
                RunningMode.NORMAL, true, true, null);
        assertThat(Files.exists(uuidPath), is(true));
        List<String> uuids = Files.readAllLines(uuidPath);
        assertThat(uuids, is(not(nullValue())));
        assertThat(uuids.size(), is(1));
        String uuid = uuids.get(0);
        //Check nothing happens on startup if file is already there
        hcEnvironment = new HostControllerEnvironment(hostProperties, false, "",
                InetAddress.getLocalHost(), 8080, InetAddress.getLocalHost(), 9990, null, null, null, null, null,
                RunningMode.NORMAL, true, true, null);
        assertThat(Files.exists(uuidPath), is(true));
        uuids = Files.readAllLines(uuidPath);
        assertThat(uuids, is(not(nullValue())));
        assertThat(uuids.size(), is(1));
        assertThat(uuids.get(0), is(uuid));
        //Check re-creation on startup
        Files.delete(uuidPath);
        assertThat(Files.notExists(uuidPath), is(true));
        hcEnvironment = new HostControllerEnvironment(hostProperties, false, "",
                InetAddress.getLocalHost(), 8080, InetAddress.getLocalHost(), 9990, null, null, null, null, null,
                RunningMode.NORMAL, true, true, null);
        assertThat(Files.exists(uuidPath), is(true));
        uuids = Files.readAllLines(uuidPath);
        assertThat(uuids, is(not(nullValue())));
        assertThat(uuids.size(), is(1));
        assertThat(uuids.get(0), is(not(uuid)));
        Files.delete(uuidPath);
    }
}
