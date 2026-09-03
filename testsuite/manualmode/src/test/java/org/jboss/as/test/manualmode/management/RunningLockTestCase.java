/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import jakarta.inject.Inject;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class RunningLockTestCase {

    private static final String INSTALLATION_DIR = ".installation";
    private static final String RUNNING_LOCK_FILE = "running.lock";

    @Inject
    private ServerController container;

    private Path lockFilePath;

    @Before
    public void setUp() throws Exception {
        Path jbossHome = Paths.get(TestSuiteEnvironment.getJBossHome());
        lockFilePath = jbossHome.resolve(INSTALLATION_DIR).resolve(RUNNING_LOCK_FILE);
        Files.createDirectories(lockFilePath.getParent());
        if (!Files.exists(lockFilePath)) {
            Files.createFile(lockFilePath);
        }
    }

    @After
    public void tearDown() {
        if (container.isStarted()) {
            container.stop();
        }
    }

    @Test
    public void testLockIsAcquiredOnStart() throws Exception {
        container.start();
        assertLockHeld("Server should hold the running lock after start");
    }

    @Test
    public void testLockIsHeldAcrossReload() throws Exception {
        container.start();
        assertLockHeld("Server should hold the running lock before reload");
        container.reload();
        assertLockHeld("Server should hold the running lock after reload (no gap)");
    }

    @Test
    public void testLockIsReleasedOnStop() throws Exception {
        container.start();
        assertLockHeld("Server should hold the running lock while running");
        container.stop();
        assertLockFree("Server should have released the running lock after stop");
    }

    /**
     * Asserts the lock file is currently held by another process (the server).
     *
     * On Linux, {@code tryLock()} returns {@code null} when another process holds the lock.
     * On Windows, the mandatory lock may prevent even opening the file, throwing an
     * {@link IOException} — both outcomes indicate the server is running.
     */
    private void assertLockHeld(String message) throws Exception {
        try (FileChannel channel = FileChannel.open(lockFilePath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                lock.release();
                Assert.fail(message + ": tryLock() succeeded but expected server to hold the lock");
            }
        } catch (IOException e) {
            // On Windows, a mandatory lock held by the server prevents opening the file.
            // This is expected — it means the server is running.
        }
    }

    /**
     * Asserts the lock file is not held by any process.
     */
    private void assertLockFree(String message) throws Exception {
        try (FileChannel channel = FileChannel.open(lockFilePath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            FileLock lock = channel.tryLock();
            Assert.assertNotNull(message + ": tryLock() returned null but expected the lock to be free", lock);
            lock.release();
        }
    }
}
