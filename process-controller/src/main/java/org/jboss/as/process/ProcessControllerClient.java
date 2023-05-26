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

package org.jboss.as.process;

import static org.jboss.as.process.protocol.StreamUtils.readFully;
import static org.jboss.as.process.protocol.StreamUtils.readInt;
import static org.jboss.as.process.protocol.StreamUtils.readLong;
import static org.jboss.as.process.protocol.StreamUtils.readUTFZBytes;
import static org.jboss.as.process.protocol.StreamUtils.readUnsignedByte;
import static org.jboss.as.process.protocol.StreamUtils.safeClose;
import static org.jboss.as.process.protocol.StreamUtils.writeBoolean;
import static org.jboss.as.process.protocol.StreamUtils.writeInt;
import static org.jboss.as.process.protocol.StreamUtils.writeUTFZBytes;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.process.logging.ProcessLogger;
import org.jboss.as.process.protocol.Connection;
import org.jboss.as.process.protocol.MessageHandler;
import org.jboss.as.process.protocol.ProtocolClient;
import org.jboss.as.process.protocol.StreamUtils;
import org.wildfly.common.Assert;

/**
 * A client to the Process Controller.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ProcessControllerClient implements Closeable {

    public static final String HOST_CONTROLLER_PROCESS_NAME = Main.HOST_CONTROLLER_PROCESS_NAME;

    private final Connection connection;

    ProcessControllerClient(final Connection connection) {
        this.connection = connection;
    }

    public static ProcessControllerClient connect(final ProtocolClient.Configuration configuration, final String authCode, final ProcessMessageHandler messageHandler) throws IOException {
        Assert.checkNotNullParam("configuration", configuration);
        Assert.checkNotNullParam("authCode", authCode);
        Assert.checkNotNullParam("messageHandler", messageHandler);
        configuration.setMessageHandler(new MessageHandler() {
            public void handleMessage(final Connection connection, final InputStream dataStream) throws IOException {
                final ProcessControllerClient client = (ProcessControllerClient) connection.getAttachment();
                final int cmd = readUnsignedByte(dataStream);
                switch (cmd) {
                    case Protocol.PROCESS_ADDED: {
                        final String processName = readUTFZBytes(dataStream);
                        dataStream.close();
                        ProcessLogger.CLIENT_LOGGER.tracef("Received process_added for process %s", processName);
                        messageHandler.handleProcessAdded(client, processName);
                        break;
                    }
                    case Protocol.PROCESS_STARTED: {
                        final String processName = readUTFZBytes(dataStream);
                        dataStream.close();
                        ProcessLogger.CLIENT_LOGGER.tracef("Received process_started for process %s", processName);
                        messageHandler.handleProcessStarted(client, processName);
                        break;
                    }
                    case Protocol.PROCESS_STOPPED: {
                        final String processName = readUTFZBytes(dataStream);
                        final long uptimeMillis = readLong(dataStream);
                        dataStream.close();
                        ProcessLogger.CLIENT_LOGGER.tracef("Received process_stopped for process %s", processName);
                        messageHandler.handleProcessStopped(client, processName, uptimeMillis);
                        break;
                    }
                    case Protocol.PROCESS_REMOVED: {
                        final String processName = readUTFZBytes(dataStream);
                        dataStream.close();
                        ProcessLogger.CLIENT_LOGGER.tracef("Received process_removed for process %s", processName);
                        messageHandler.handleProcessRemoved(client, processName);
                        break;
                    }
                    case Protocol.PROCESS_INVENTORY: {
                        final int cnt = readInt(dataStream);
                        final Map<String, ProcessInfo> inventory = new HashMap<String, ProcessInfo>();
                        for (int i = 0; i < cnt; i++) {
                            final String processName = readUTFZBytes(dataStream);
                            final byte[] processAuthBytes = new byte[ProcessController.AUTH_BYTES_ENCODED_LENGTH];
                            readFully(dataStream, processAuthBytes);
                            final boolean processRunning = StreamUtils.readBoolean(dataStream);
                            final boolean processStopping = StreamUtils.readBoolean(dataStream);
                            final String processAuthKey = new String(processAuthBytes, StandardCharsets.US_ASCII);
                            inventory.put(processName, new ProcessInfo(processName, processAuthKey, processRunning, processStopping));
                        }
                        dataStream.close();
                        ProcessLogger.CLIENT_LOGGER.tracef("Received process_inventory");
                        messageHandler.handleProcessInventory(client, inventory);
                        break;
                    } case Protocol.OPERATION_FAILED : {
                        final int operationType = readUnsignedByte(dataStream);
                        final ProcessMessageHandler.OperationType type = ProcessMessageHandler.OperationType.fromCode(operationType);
                        final String processName = readUTFZBytes(dataStream);
                        dataStream.close();
                        ProcessLogger.CLIENT_LOGGER.tracef("Received operation_failed for process %s", processName);
                        messageHandler.handleOperationFailed(client, type, processName);
                        break;
                    } default: {
                        ProcessLogger.CLIENT_LOGGER.receivedUnknownMessageCode(cmd);
                        // ignore
                        dataStream.close();
                        break;
                    }
                }
            }

            public void handleShutdown(final Connection connection) throws IOException {
                final ProcessControllerClient client = (ProcessControllerClient) connection.getAttachment();
                messageHandler.handleConnectionShutdown(client);
            }

            public void handleFailure(final Connection connection, final IOException cause) throws IOException {
                final ProcessControllerClient client = (ProcessControllerClient) connection.getAttachment();
                messageHandler.handleConnectionFailure(client, cause);
            }

            public void handleFinished(final Connection connection) throws IOException {
                final ProcessControllerClient client = (ProcessControllerClient) connection.getAttachment();
                messageHandler.handleConnectionFinished(client);
            }
        });
        final ProtocolClient client = new ProtocolClient(configuration);
        final Connection connection = client.connect();
        boolean ok = false;
        try {
            final OutputStream os = connection.writeMessage();
            try {
                os.write(Protocol.AUTH);
                os.write(1);
                os.write(authCode.getBytes(StandardCharsets.US_ASCII));
                final ProcessControllerClient processControllerClient = new ProcessControllerClient(connection);
                connection.attach(processControllerClient);
                ProcessLogger.CLIENT_LOGGER.trace("Sent initial greeting message");
                os.close();
                ok = true;
                return processControllerClient;
            } finally {
                safeClose(os);
            }
        } finally {
            if (! ok) {
                safeClose(connection);
            }
        }
    }

    public OutputStream sendStdin(String processName) throws IOException {
        final OutputStream os = connection.writeMessage();
        boolean ok = false;
        try {
            os.write(Protocol.SEND_STDIN);
            writeUTFZBytes(os, processName);
            ok = true;
            return os;
        } finally {
            if (! ok) {
                safeClose(os);
            }
        }
    }

    public void addProcess(String processName, int processId, String[] cmd, String workingDir, Map<String, String> env) throws IOException {
        Assert.checkNotNullParam("processName", processName);
        Assert.checkNotNullParam("cmd", cmd);
        Assert.checkNotNullParam("workingDir", workingDir);
        Assert.checkNotNullParam("env", env);
        // fixme
        Assert.checkNotEmptyParam("cmd", Arrays.asList(cmd));

        final OutputStream os = connection.writeMessage();
        try {
            os.write(Protocol.ADD_PROCESS);
            writeUTFZBytes(os, processName);
            writeInt(os, processId);
            writeInt(os, cmd.length);
            for (String c : cmd) {
                writeUTFZBytes(os, c);
            }
            writeInt(os, env.size());
            for (String key : env.keySet()) {
                final String value = env.get(key);
                writeUTFZBytes(os, key);
                if (value != null) {
                    writeUTFZBytes(os, value);
                } else {
                    writeUTFZBytes(os, "");
                }
            }
            writeUTFZBytes(os, workingDir);
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public void startProcess(String processName) throws IOException {
        Assert.checkNotNullParam("processName", processName);
        final OutputStream os = connection.writeMessage();
        try {
            os.write(Protocol.START_PROCESS);
            writeUTFZBytes(os, processName);
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public void stopProcess(String processName) throws IOException {
        Assert.checkNotNullParam("processName", processName);
        final OutputStream os = connection.writeMessage();
        try {
            os.write(Protocol.STOP_PROCESS);
            writeUTFZBytes(os, processName);
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public void removeProcess(String processName) throws IOException {
        Assert.checkNotNullParam("processName", processName);
        final OutputStream os = connection.writeMessage();
        try {
            os.write(Protocol.REMOVE_PROCESS);
            writeUTFZBytes(os, processName);
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public void requestProcessInventory() throws IOException {
        final OutputStream os = connection.writeMessage();
        try {
            os.write(Protocol.REQUEST_PROCESS_INVENTORY);
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public void reconnectServerProcess(final String processName, final URI managementURI, final boolean managementSubsystemEndpoint, final String serverAuthToken) throws IOException {
        // This call is specifically about asking a domain server to reconnect to the host controller.
        Assert.checkNotNullParam("processName", processName);
        final OutputStream os = connection.writeMessage();
        try{
            os.write(Protocol.RECONNECT_PROCESS);
            writeUTFZBytes(os, processName);
            writeUTFZBytes(os, managementURI.getScheme());
            writeUTFZBytes(os, managementURI.getHost());
            writeInt(os, managementURI.getPort());
            writeBoolean(os, managementSubsystemEndpoint);
            writeUTFZBytes(os, serverAuthToken);
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public void shutdown() throws IOException {
        shutdown(0);
    }

    public void shutdown(int exitCode) throws IOException {
        final OutputStream os = connection.writeMessage();
        try {
            os.write(Protocol.SHUTDOWN);
            writeInt(os, exitCode);
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public void destroyProcess(String processName) throws IOException {
        Assert.checkNotNullParam("processName", processName);
        final OutputStream os = connection.writeMessage();
        try {
            os.write(Protocol.DESTROY_PROECESS);
            writeUTFZBytes(os, processName);
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public void killProcess(String processName) throws IOException {
        Assert.checkNotNullParam("processName", processName);
        final OutputStream os = connection.writeMessage();
        try {
            os.write(Protocol.KILL_PROCESS);
            writeUTFZBytes(os, processName);
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public void close() throws IOException {
        connection.close();
    }
}
