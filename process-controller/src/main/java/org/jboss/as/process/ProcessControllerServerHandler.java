/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process;

import static org.jboss.as.process.protocol.StreamUtils.readBoolean;
import static org.jboss.as.process.protocol.StreamUtils.readFully;
import static org.jboss.as.process.protocol.StreamUtils.readInt;
import static org.jboss.as.process.protocol.StreamUtils.readUTFZBytes;
import static org.jboss.as.process.protocol.StreamUtils.readUnsignedByte;
import static org.jboss.as.process.protocol.StreamUtils.safeClose;
import static org.jboss.as.process.protocol.StreamUtils.writeUTFZBytes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.process.logging.ProcessLogger;
import org.jboss.as.process.protocol.Connection;
import org.jboss.as.process.protocol.ConnectionHandler;
import org.jboss.as.process.protocol.MessageHandler;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ProcessControllerServerHandler implements ConnectionHandler {

    private final ProcessController processController;

    public ProcessControllerServerHandler(final ProcessController controller) {
        processController = controller;
    }

    public MessageHandler handleConnected(final Connection connection) throws IOException {
        ProcessLogger.SERVER_LOGGER.tracef("Received connection from %s", connection.getPeerAddress());
        return new InitMessageHandler(processController);
    }

    private static class InitMessageHandler implements MessageHandler {

        private final ProcessController processController;

        public InitMessageHandler(final ProcessController processController) {
            this.processController = processController;
        }

        public void handleMessage(final Connection connection, final InputStream dataStream) throws IOException {
            final int cmd = readUnsignedByte(dataStream);
            if (cmd != Protocol.AUTH) {
                ProcessLogger.SERVER_LOGGER.receivedUnknownGreetingCode(cmd, connection.getPeerAddress());
                connection.close();
                return;
            }
            final int version = readUnsignedByte(dataStream);
            if (version < 1) {
                ProcessLogger.SERVER_LOGGER.receivedInvalidVersion(connection.getPeerAddress());
                connection.close();
                return;
            }
            final byte[] authCode = new byte[ProcessController.AUTH_BYTES_ENCODED_LENGTH];
            readFully(dataStream, authCode);
            final ManagedProcess process = processController.getServerByAuthCode(authCode);
            if (process == null) {
                ProcessLogger.SERVER_LOGGER.receivedUnknownCredentials(connection.getPeerAddress());
                safeClose(connection);
                return;
            }
            ProcessLogger.SERVER_LOGGER.tracef("Received authentic connection from %s", connection.getPeerAddress());
            connection.setMessageHandler(new ConnectedMessageHandler(processController, process.isPrivileged()));
            processController.addManagedConnection(connection);
            dataStream.close();
            // Reset the respawn count for the connecting process
            process.resetRespawnCount();
        }

        public void handleShutdown(final Connection connection) throws IOException {
            ProcessLogger.SERVER_LOGGER.tracef("Received end-of-stream for connection");
            processController.removeManagedConnection(connection);
            connection.shutdownWrites();
        }

        public void handleFailure(final Connection connection, final IOException e) throws IOException {
            ProcessLogger.SERVER_LOGGER.tracef(e, "Received failure of connection");
            processController.removeManagedConnection(connection);
            connection.close();
        }

        public void handleFinished(final Connection connection) throws IOException {
            ProcessLogger.SERVER_LOGGER.tracef("Connection finished");
            processController.removeManagedConnection(connection);
            // nothing
        }

        private static class ConnectedMessageHandler implements MessageHandler {

            private final boolean isPrivileged;

            private final ProcessController processController;

            public ConnectedMessageHandler(final ProcessController processController, final boolean isHostController) {
                this.processController = processController;
                this.isPrivileged = isHostController;
            }

            public void handleMessage(final Connection connection, final InputStream dataStream) throws IOException {
                ProcessMessageHandler.OperationType operationType = null;
                String processName = null;
                try {
                    final int cmd = readUnsignedByte(dataStream);
                    switch (cmd) {
                        case Protocol.SEND_STDIN: {
                            // HostController only
                            if (isPrivileged) {
                                operationType = ProcessMessageHandler.OperationType.SEND_STDIN;
                                processName = readUTFZBytes(dataStream);
                                ProcessLogger.SERVER_LOGGER.tracef("Received send_stdin for process %s", processName);
                                processController.sendStdin(processName, dataStream);
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring send_stdin message from untrusted source");
                            }
                            dataStream.close();
                            break;
                        }
                        case Protocol.ADD_PROCESS: {
                            if (isPrivileged) {
                                operationType = ProcessMessageHandler.OperationType.ADD;
                                processName = readUTFZBytes(dataStream);
                                final int processId = readInt(dataStream);
                                final int commandCount = readInt(dataStream);
                                final String[] command = new String[commandCount];
                                for (int i = 0; i < commandCount; i ++) {
                                    command[i] = readUTFZBytes(dataStream);
                                }
                                final int envCount = readInt(dataStream);
                                final Map<String, String> env = new HashMap<String, String>();
                                for (int i = 0; i < envCount; i ++) {
                                    env.put(readUTFZBytes(dataStream), readUTFZBytes(dataStream));
                                }
                                final String workingDirectory = readUTFZBytes(dataStream);
                                ProcessLogger.SERVER_LOGGER.tracef("Received add_process for process %s", processName);
                                processController.addProcess(processName, processId, Arrays.asList(command), env, workingDirectory, false, false);
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring add_process message from untrusted source");
                            }
                            dataStream.close();
                            break;
                        }
                        case Protocol.START_PROCESS: {
                            if (isPrivileged) {
                                operationType = ProcessMessageHandler.OperationType.START;
                                processName = readUTFZBytes(dataStream);
                                processController.startProcess(processName);
                                ProcessLogger.SERVER_LOGGER.tracef("Received start_process for process %s", processName);
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring start_process message from untrusted source");
                            }
                            dataStream.close();
                            break;
                        }
                        case Protocol.STOP_PROCESS: {
                            if (isPrivileged) {
                                operationType = ProcessMessageHandler.OperationType.STOP;
                                processName = readUTFZBytes(dataStream);
                                // HostController only
                                processController.stopProcess(processName);
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring stop_process message from untrusted source");
                            }
                            dataStream.close();
                            break;
                        }
                        case Protocol.REMOVE_PROCESS: {
                            if (isPrivileged) {
                                operationType = ProcessMessageHandler.OperationType.REMOVE;
                                processName = readUTFZBytes(dataStream);
                                processController.removeProcess(processName);
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring remove_process message from untrusted source");
                            }
                            dataStream.close();
                            break;
                        }
                        case Protocol.REQUEST_PROCESS_INVENTORY: {
                            if (isPrivileged) {
                                operationType = ProcessMessageHandler.OperationType.INVENTORY;
                                processController.sendInventory();
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring request_process_inventory message from untrusted source");
                            }
                            dataStream.close();
                            break;
                        }
                        case Protocol.RECONNECT_PROCESS: {
                            if (isPrivileged) {
                                operationType = ProcessMessageHandler.OperationType.REMOVE;
                                processName = readUTFZBytes(dataStream);
                                final String scheme = readUTFZBytes(dataStream);
                                final String hostName = readUTFZBytes(dataStream);
                                final int port = readInt(dataStream);
                                final boolean managementSubsystemEndpoint = readBoolean(dataStream);
                                final String serverAuthToken = readUTFZBytes(dataStream);
                                processController.sendReconnectServerProcess(processName, scheme, hostName, port, managementSubsystemEndpoint, serverAuthToken);
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring reconnect_process message from untrusted source");
                            }
                            dataStream.close();
                            break;
                        } case Protocol.SHUTDOWN: {
                            if (isPrivileged) {
                                final int exitCode = readInt(dataStream);
                                new Thread(new Runnable() {
                                    public void run() {
                                        processController.shutdown();
                                        System.exit(exitCode);
                                    }
                                }).start();
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring shutdown message from untrusted source");
                            }
                            break;
                        }
                        case Protocol.DESTROY_PROECESS: {
                            if (isPrivileged) {
                                operationType = ProcessMessageHandler.OperationType.STOP;
                                processName = readUTFZBytes(dataStream);
                                processController.destroyProcess(processName);
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring destroy_process message from untrusted source");
                            }
                            dataStream.close();
                            break;
                        }
                        case Protocol.KILL_PROCESS: {
                            if (isPrivileged) {
                                operationType = ProcessMessageHandler.OperationType.STOP;
                                processName = readUTFZBytes(dataStream);
                                processController.killProcess(processName);
                            } else {
                                ProcessLogger.SERVER_LOGGER.tracef("Ignoring kill_process message from untrusted source");
                            }
                            dataStream.close();
                            break;
                        }
                        default: {
                            ProcessLogger.SERVER_LOGGER.receivedUnknownMessageCode(cmd);
                            // unknown
                            dataStream.close();
                        }
                    }
                } catch(IOException e) {
                    if(operationType != null && processName != null) {
                        safeClose(dataStream);
                        try {
                            final OutputStream os = connection.writeMessage();
                            try {
                                os.write(Protocol.OPERATION_FAILED);
                                os.write(operationType.getCode());
                                writeUTFZBytes(os, processName);
                                os.close();
                            } finally {
                                safeClose(os);
                            }
                        } catch (IOException ignore) {
                            ProcessLogger.ROOT_LOGGER.debugf(ignore, "failed to write operation failed message");
                        }
                    }
                    throw e;
                } finally {
                    safeClose(dataStream);
                }
            }

            public void handleShutdown(final Connection connection) throws IOException {
                ProcessLogger.SERVER_LOGGER.tracef("Received end-of-stream for connection");
                processController.removeManagedConnection(connection);
                connection.shutdownWrites();
            }

            public void handleFailure(final Connection connection, final IOException e) throws IOException {
                ProcessLogger.SERVER_LOGGER.tracef(e, "Received failure of connection");
                processController.removeManagedConnection(connection);
                connection.close();
            }

            public void handleFinished(final Connection connection) throws IOException {
                ProcessLogger.SERVER_LOGGER.tracef("Connection finished");
                processController.removeManagedConnection(connection);
                connection.close();
            }
        }
    }
}
