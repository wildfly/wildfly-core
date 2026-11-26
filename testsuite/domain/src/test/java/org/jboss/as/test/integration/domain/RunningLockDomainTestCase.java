/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that the advisory running lock at {@code WFLY_HOME/.installation/running.lock}
 * is correctly acquired and released by the Process Controller in domain mode.
 */
public class RunningLockDomainTestCase {

    private static final String INSTALLATION_DIR = ".installation";
    private static final String RUNNING_LOCK_FILE = "running.lock";

    private DomainTestSupport testSupport;
    private DomainLifecycleUtil primaryUtil;
    private Path lockFilePath;

    @Before
    public void setUp() throws Exception {
        testSupport = DomainTestSupport.create(
            DomainTestSupport.Configuration.create(
                RunningLockDomainTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml",
                "host-configs/host-primary.xml",
                "host-configs/host-secondary.xml"
            )
        );
        primaryUtil = testSupport.getDomainPrimaryLifecycleUtil();
        Path jbossHome = Paths.get(testSupport.getDomainPrimaryConfiguration().getJbossHome());
        lockFilePath = jbossHome.resolve(INSTALLATION_DIR).resolve(RUNNING_LOCK_FILE);
        Assert.assertTrue("Running lock file must exist at " + lockFilePath, lockFilePath.toFile().exists());
        Assert.assertTrue("Running lock file must be writable at " + lockFilePath, lockFilePath.toFile().canWrite());
    }

    @After
    public void tearDown() {
        if (testSupport != null) {
            testSupport.close();
            testSupport = null;
        }
    }

    @Test
    public void testLockIsAcquiredOnStart() throws Exception {
        testSupport.start();
        assertLockHeld("Domain should hold the running lock after start");
    }

    @Test
    public void testLockIsHeldAcrossReload() throws Exception {
        testSupport.start();
        assertLockHeld("Domain should hold the running lock before HC reload");
        primaryUtil.reload("primary");
        assertLockHeld("Domain should hold the running lock after HC reload (no gap)");
    }

    @Test
    public void testLockIsReleasedOnStop() throws Exception {
        testSupport.start();
        assertLockHeld("Domain should hold the running lock while running");
        testSupport.stopHosts();
        assertLockFree("Domain should have released the running lock after stop");
    }

    @Test
    public void testHostStartsEvenWhenLockAlreadyHeld() throws Exception {
        try (FileChannel externalChannel = FileChannel.open(lockFilePath,
                StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            FileLock externalLock = externalChannel.lock();
            Assert.assertNotNull("Test setup failed - couldn't acquire external lock", externalLock);

            testSupport.start();
            Assert.assertNotNull("Domain should have started even when lock is already held",
                    primaryUtil.getDomainClient());
        }
    }

    /**
     * Asserts the lock file is currently held by another process (the Process Controller).
     *
     * On Linux, {@code tryLock()} returns {@code null} when another process holds the lock.
     * On Windows, the mandatory lock may prevent even opening the file, throwing an
     * {@link IOException} — both outcomes indicate the domain is running.
     */
    private void assertLockHeld(String message) throws Exception {
        try (FileChannel channel = FileChannel.open(lockFilePath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                lock.release();
                Assert.fail(message + ": tryLock() succeeded but expected domain to hold the lock");
            }
        } catch (IOException e) {
            // On Windows, mandatory lock held by the Process Controller prevents opening the file.
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
