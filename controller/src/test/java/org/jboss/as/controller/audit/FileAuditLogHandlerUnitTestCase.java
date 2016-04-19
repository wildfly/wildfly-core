/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
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
 *
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
