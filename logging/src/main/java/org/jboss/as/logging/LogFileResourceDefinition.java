/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging;

import static org.jboss.as.logging.CommonAttributes.ENCODING;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LogFileResourceDefinition extends SimpleResourceDefinition {

    static final AccessConstraintDefinition VIEW_SERVER_LOGS = new SensitiveTargetAccessConstraintDefinition(
            new SensitivityClassification(LoggingExtension.SUBSYSTEM_NAME, "view-server-logs", false, false, false));

    static final String NAME = "log-file";
    static final String ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private static final SimpleAttributeDefinition FILE_SIZE = SimpleAttributeDefinitionBuilder.create("file-size", ModelType.LONG, false)
            .setStorageRuntime()
            .setAllowExpression(false)
            .build();

    private static final SimpleAttributeDefinition LAST_MODIFIED_TIME = SimpleAttributeDefinitionBuilder.create("last-modified-time", ModelType.LONG, false)
            .setStorageRuntime()
            .setAllowExpression(false)
            .build();

    private static final SimpleAttributeDefinition LAST_MODIFIED_TIMESTAMP = SimpleAttributeDefinitionBuilder.create("last-modified-timestamp", ModelType.STRING, false)
            .setStorageRuntime()
            .setAllowExpression(false)
            .build();

    private static final SimpleAttributeDefinition STREAM = SimpleAttributeDefinitionBuilder.create("stream", ModelType.STRING)
            .setStorageRuntime()
            .setRequired(false)
            .build();

    private static final SimpleAttributeDefinition LINES = SimpleAttributeDefinitionBuilder.create("lines", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(10))
            .setValidator(new IntRangeValidator(-1, true))
            .build();

    private static final SimpleAttributeDefinition SKIP = SimpleAttributeDefinitionBuilder.create("skip", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.ZERO)
            .setValidator(new IntRangeValidator(0, true))
            .build();

    private static final SimpleAttributeDefinition TAIL = SimpleAttributeDefinitionBuilder.create("tail", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    private static final SimpleOperationDefinition READ_LOG_FILE = new SimpleOperationDefinitionBuilder("read-log-file", LoggingExtension.getResourceDescriptionResolver())
            .addAccessConstraint(VIEW_SERVER_LOGS)
            .setParameters(ENCODING, LINES, SKIP, TAIL)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.STRING)
            .setReadOnly()
            .setRuntimeOnly()
            .build();

    private static final PathElement LOG_FILE_PATH = PathElement.pathElement("log-file");

    private final PathManager pathManager;

    LogFileResourceDefinition(final PathManager pathManager) {
        super(new Parameters(LOG_FILE_PATH, LoggingExtension.getResourceDescriptionResolver("log-file"))
                .setRuntime().setAccessConstraints(VIEW_SERVER_LOGS));
        assert pathManager != null : "PathManager cannot be null";
        this.pathManager = pathManager;
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(READ_LOG_FILE, new ReadLogFileOperation(pathManager));

    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadOnlyAttribute(FILE_SIZE, new ReadAttributeOperationStepHandler() {
            @Override
            protected void updateModel(final Path path, final ModelNode model) throws IOException {
                model.set(Files.size(path));
            }
        });
        resourceRegistration.registerReadOnlyAttribute(LAST_MODIFIED_TIME, new ReadAttributeOperationStepHandler() {
            @Override
            protected void updateModel(final Path path, final ModelNode model) throws IOException {
                model.set(Files.getLastModifiedTime(path).toMillis());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(LAST_MODIFIED_TIMESTAMP, new ReadAttributeOperationStepHandler() {
            @Override
            protected void updateModel(final Path path, final ModelNode model) throws IOException {
                final SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_FORMAT);
                model.set(sdf.format(new Date(Files.getLastModifiedTime(path).toMillis())));
            }
        });

        final OperationStepHandler streamHandler = new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final String name = context.getCurrentAddressValue();
                final String logDir = pathManager.getPathEntry(ServerEnvironment.SERVER_LOG_DIR).resolvePath();
                validateFile(context, logDir, name);
                final Path path = Paths.get(logDir, name);
                try {
                    String uuid = context.attachResultStream("text/plain", Files.newInputStream(path));
                    context.getResult().set(uuid);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        resourceRegistration.registerReadOnlyAttribute(STREAM, streamHandler);
    }

    private abstract class ReadAttributeOperationStepHandler implements OperationStepHandler {

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final ModelNode model = context.getResult();
            final String name = context.getCurrentAddressValue();
            final String logDir = pathManager.getPathEntry(ServerEnvironment.SERVER_LOG_DIR).resolvePath();
            validateFile(context, logDir, name);
            final Path path = Paths.get(logDir, name);
            if (Files.notExists(path)) {
                throw LoggingLogger.ROOT_LOGGER.logFileNotFound(name, logDir);
            }
            try {
                updateModel(path, model);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            context.completeStep(ResultHandler.NOOP_RESULT_HANDLER);
        }

        protected abstract void updateModel(Path path, ModelNode model) throws IOException;
    }


    /**
     * Reads a log file and returns the results.
     * <p/>
     * <i>Note: </i> If this operation ends up being repeatedly invoked, from the web console for instance, there could
     * be a performance impact as the model is read and processed for file names during each invocation
     */
    static class ReadLogFileOperation implements OperationStepHandler {

        private final PathManager pathManager;

        private ReadLogFileOperation(final PathManager pathManager) {
            this.pathManager = pathManager;
        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final String fileName = context.getCurrentAddressValue();
            final String logDir = pathManager.getPathEntry(ServerEnvironment.SERVER_LOG_DIR).resolvePath();
            validateFile(context, logDir, fileName);
            // Validate the operation
            for (AttributeDefinition attribute : READ_LOG_FILE.getParameters()) {
                attribute.validateOperation(operation);
            }
            final int numberOfLines = LINES.resolveModelAttribute(context, operation).asInt();
            final int skip = SKIP.resolveModelAttribute(context, operation).asInt();
            final boolean tail = TAIL.resolveModelAttribute(context, operation).asBoolean();
            final ModelNode encodingModel = ENCODING.resolveModelAttribute(context, operation);
            final String encoding = (encodingModel.isDefined() ? encodingModel.asString() : null);
            final File path = new File(pathManager.resolveRelativePathEntry(fileName, ServerEnvironment.SERVER_LOG_DIR));

            // The file must exist
            if (!path.exists()) {
                throw LoggingLogger.ROOT_LOGGER.logFileNotFound(fileName, ServerEnvironment.SERVER_LOG_DIR);
            }

            // Read the contents of the log file
            try {
                final List<String> lines;
                if (numberOfLines == 0) {
                    lines = Collections.emptyList();
                } else {
                    lines = readLines(path, encoding, tail, skip, numberOfLines);
                }
                final ModelNode result = context.getResult().setEmptyList();
                for (String line : lines) {
                    result.add(line);
                }
            } catch (IOException e) {
                throw LoggingLogger.ROOT_LOGGER.failedToReadLogFile(e, fileName);
            }
            context.completeStep(ResultHandler.NOOP_RESULT_HANDLER);
        }

        private List<String> readLines(final File file, final String encoding, final boolean tail, final int skip, final int numberOfLines) throws IOException {
            final List<String> lines;
            if (numberOfLines < 0) {
                lines = new ArrayList<>();
            } else {
                lines = new ArrayList<>(numberOfLines);
            }
            try (
                    final InputStream in = (tail ? new LifoFileInputStream(file) : Files.newInputStream(file.toPath()));
                    /* we should stick with the default here and not use UTF-8.
                    The encoding on the file handler does not default to UTF-8 but the system default.
                    I think here we should stick with the system default unless explicitly defined.
                    I could see a UTF-8 default possibly being problematic on IBM bases systems.
                     */
                    final InputStreamReader isr = (encoding == null ? new InputStreamReader(in) : new InputStreamReader(in, encoding));
                    final BufferedReader reader = new BufferedReader(isr)
            ) {
                int lineCount = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (++lineCount <= skip) continue;
                    if (lines.size() == numberOfLines) break;
                    lines.add(line);
                }
                if (tail) {
                    Collections.reverse(lines);
                }
                return lines;
            }
        }
    }

    static final class LifoFileInputStream extends InputStream {
        private final RandomAccessFile raf;
        private final long len;
        private long start;
        private long end;
        private long pos;

        LifoFileInputStream(final File file) throws IOException {
            raf = new RandomAccessFile(file, "r");
            len = raf.length();
            start = len;
            end = len;
            pos = end;
        }

        private void positionFile() throws IOException {
            end = start;
            // If we're at the beginning of the file, nothing more to read
            if (end == 0) {
                end = -1;
                start = -1;
                pos = -1;
                return;
            }

            long filePointer = start - 1;
            while (true) {
                filePointer--;
                // We're at the start of the file
                if (filePointer < 0) {
                    break;
                }
                // Position the file
                raf.seek(filePointer);
                final byte readByte = raf.readByte();
                // If the byte is a line feed we've found the next line ignoring the last line feed in the file
                if (readByte == '\n' && filePointer != (len - 1)) {
                    break;
                }
            }
            start = filePointer + 1;
            pos = start;
        }

        @Override
        public int read() throws IOException {
            if (pos < end) {
                raf.seek(pos++);
                return raf.readByte();
            } else if (pos < 0) {
                return -1;
            } else {
                positionFile();
                return read();
            }
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }

    private static void validateFile(final OperationContext context, final String logDir, final String fileName) throws OperationFailedException {
        // Ensure the resource exists
        context.readResource(PathAddress.EMPTY_ADDRESS);
        final Path dir = Paths.get(logDir);
        final AtomicBoolean found = new AtomicBoolean();
        try {
            // Walk the log directory and only allow files within the log directory to be read
            Files.walkFileTree(dir, Collections.singleton(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    final Path relativeFile = dir.relativize(file);
                    if (fileName.equals(relativeFile.toString())) {
                        found.set(true);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw LoggingLogger.ROOT_LOGGER.failedToReadLogFile(e, fileName);
        }
        if (!found.get()) {
            throw LoggingLogger.ROOT_LOGGER.readNotAllowed(fileName);
        }
    }
}
