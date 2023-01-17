/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.logging;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test to confirm rotate-on-boot works.
 *
 * @author <a href="mailto:pkremens@redhat.com">Petr Kremensky</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class SizeAppenderRestartTestCase extends AbstractLoggingTestCase {
    private static final String FILE_NAME = "sizeAppenderRestartTestCase.log";
    private static final String SIZE_HANDLER_NAME = "sizeAppenderRestartTestCase";
    private static final ModelNode SIZE_HANDLER_ADDRESS = createAddress("size-rotating-file-handler", SIZE_HANDLER_NAME);
    private static final ModelNode ROOT_LOGGER_ADDRESS = createAddress("root-logger", "ROOT");
    private Path logFile;

    @Before
    public void startContainer() throws Exception {
        // Start the server
        Assert.assertNotNull(container);
        container.start();
        logFile = getAbsoluteLogFilePath(FILE_NAME);
        // Deploy the servlet
        deploy();

        // Create the size-rotating handler
        ModelNode op = Operations.createAddOperation(SIZE_HANDLER_ADDRESS);
        ModelNode file = new ModelNode();
        file.get("path").set(logFile.normalize().toString());
        op.get(FILE).set(file);
        executeOperation(op);

        // Add the handler to the root-logger
        op = Operations.createOperation("add-handler", ROOT_LOGGER_ADDRESS);
        op.get(NAME).set(SIZE_HANDLER_NAME);
        executeOperation(op);
    }

    @After
    public void stopContainer() throws Exception {
        Assert.assertNotNull(container);
        Assert.assertTrue(container.isStarted()); // if container is not started, we get a NPE in container.getClient() within undeploy()
        // Remove the servlet
        undeploy();

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        // Remove the handler from the root-logger
        ModelNode op = Operations.createOperation("remove-handler", ROOT_LOGGER_ADDRESS);
        op.get(NAME).set(SIZE_HANDLER_NAME);
        builder.addStep(op);

        // Remove the size-rotating handler
        builder.addStep(Operations.createRemoveOperation(SIZE_HANDLER_ADDRESS));

        executeOperation(builder.build());

        // Stop the container
        container.stop();

        // Remove log files
        clearLogs(logFile);
    }

    /*
     * rotate-on-boot = true:   restart -> log file is rotated, logs are written to new file
     */
    @Test
    public void rotateFileOnRestartTest() throws Exception {
        final String oldMessage = "SizeAppenderRestartTestCase - This is old message";
        final String newMessage = "SizeAppenderRestartTestCase - This is new message";
        executeOperation(Operations.createWriteAttributeOperation(SIZE_HANDLER_ADDRESS, "rotate-on-boot", true));
        restartServer(true);

        // make some logs, remember file size, restart
        makeLog(oldMessage);
        checkLogs(oldMessage, logFile, true);
        restartServer(false);

        // make log to new rotated log file
        makeLog(newMessage);
        checkLogs(newMessage, logFile, true);

        // verify that file was rotated
        int count = 0;
        for (Path path : listLogFiles()) {
            final String logFileName = logFile.getFileName().toString();
            final String fileName = path.getFileName().toString();
            if (fileName.startsWith(logFileName)) {
                count++;
                if (fileName.equals(logFileName + ".1")) {
                    checkLogs(newMessage, path, false);
                    checkLogs(oldMessage, path, true);
                }
            }
        }
        Assert.assertEquals("There should be two log files", 2, count);
    }

    private void restartServer(final boolean deleteLogs) throws IOException {
        Assert.assertTrue("Container is not running", container.isStarted());
        // Stop the container
        container.stop();
        if (deleteLogs) {
            clearLogs(logFile);
        }
        // Start the server again
        container.start();
        Assert.assertTrue("Container is not started", container.isStarted());
    }

    private void makeLog(final String msg) throws Exception {
        int statusCode = getResponse(msg);
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
    }

    private Iterable<Path> listLogFiles() throws IOException {
        final Collection<Path> names = new ArrayList<>();
        Files.walkFileTree(logFile.getParent(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                names.add(file);
                return super.visitFile(file, attrs);
            }
        });
        return names;
    }

    private void clearLogs(final Path path) throws IOException {
        final String expectedName = path.getFileName().toString();
        Files.walkFileTree(path.getParent(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                final String currentName = file.getFileName().toString();
                if (currentName.startsWith(expectedName)) {
                    Files.delete(file);
                }
                return super.visitFile(file, attrs);
            }
        });
    }
}
