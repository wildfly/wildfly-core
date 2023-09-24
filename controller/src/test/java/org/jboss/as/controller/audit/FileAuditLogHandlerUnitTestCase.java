/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.audit;

import java.io.File;
import java.io.IOException;

import org.jboss.as.controller.services.path.PathManagerService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class FileAuditLogHandlerUnitTestCase {

    private File confDir = createTempDir();
    private File logFile = new File(confDir, "audit-log.log");
    private PathManagerService pathManager = new PathManagerServiceStub();

    @After
    public void cleanup() {
        for (String fileName : confDir.list()) {
            File file = new File(confDir, fileName);
            file.delete();
        }
        confDir.delete();
    }

    @Test
    public void testLogFileRotated() throws IOException {
        Assert.assertTrue("Couldn't create initial log file", logFile.createNewFile());
        initializeHandler(true);

        Assert.assertEquals("Log file wasn't rotated", 2, confDir.list().length);
    }

    @Test
    public void testLogRotationDisabled() throws IOException {
        Assert.assertTrue("Couldn't create initial log file", logFile.createNewFile());
        initializeHandler(false);

        Assert.assertEquals("Log file was rotated but shouldn't have been", 1, confDir.list().length);
    }

    private void initializeHandler(boolean rotateAtStartup) {
        FileAuditLogHandler auditLogHandler =
                new FileAuditLogHandler("name", "formatter", 0, pathManager, logFile.getPath(), null, rotateAtStartup);
        auditLogHandler.initialize();
    }

    private static File createTempDir() {
        try {
            File tempFile = File.createTempFile("test-config", "");
            if (!tempFile.delete() || !tempFile.mkdir()) {
                throw new IOException("Couldn't create temp directory.");
            }
            return tempFile;
        } catch (Exception e) {
            throw new RuntimeException("Couldn't create temp directory.", e);
        }
    }

    private static class PathManagerServiceStub extends PathManagerService {}
}
