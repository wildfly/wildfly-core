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

package org.jboss.as.server;

import static org.jboss.as.server.DomainServerCommunicationServices.createAuthenticationContext;

import static org.jboss.as.process.protocol.StreamUtils.readBoolean;
import static org.jboss.as.process.protocol.StreamUtils.readFully;
import static org.jboss.as.process.protocol.StreamUtils.readInt;
import static org.jboss.as.process.protocol.StreamUtils.readUTFZBytes;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.Arrays;

import org.jboss.as.network.NetworkUtils;
import org.jboss.as.process.ExitCodes;
import org.jboss.as.process.ProcessController;
import org.jboss.as.process.stdin.Base64InputStream;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.mgmt.domain.HostControllerClient;
import org.jboss.as.server.mgmt.domain.HostControllerConnectionService;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.stdio.LoggingOutputStream;
import org.jboss.stdio.NullInputStream;
import org.jboss.stdio.SimpleStdioContextSelector;
import org.jboss.stdio.StdioContext;
import org.jboss.threads.AsyncFuture;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * The main entry point for domain-managed server instances.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class DomainServerMain {

    /**
     * Cache of the latest {@code AuthenticationContext} based on updates received
     * from the process controller.
     */
    private static volatile AuthenticationContext latestAuthenticationContext;

    private DomainServerMain() {
    }

    /**
     * Main entry point.  Reads and executes the command object from standard input.
     *
     * @param args ignored
     */
    public static void main(String[] args) {

        final InputStream initialInput = new Base64InputStream(System.in);
        final PrintStream initialError = System.err;

        // Make sure our original stdio is properly captured.
        try {
            Class.forName(ConsoleHandler.class.getName(), true, ConsoleHandler.class.getClassLoader());
        } catch (Throwable ignored) {
        }

        // Install JBoss Stdio to avoid any nasty crosstalk.
        StdioContext.install();
        final StdioContext context = StdioContext.create(
            new NullInputStream(),
            new LoggingOutputStream(org.jboss.logmanager.Logger.getLogger("stdout"), Level.INFO),
            new LoggingOutputStream(org.jboss.logmanager.Logger.getLogger("stderr"), Level.ERROR)
        );
        StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(context));

        final byte[] asAuthBytes = new byte[ProcessController.AUTH_BYTES_ENCODED_LENGTH];
        try {
            readFully(initialInput, asAuthBytes);
        } catch (IOException e) {
            e.printStackTrace();
            SystemExiter.abort(ExitCodes.FAILED);
            throw new IllegalStateException(); // not reached
        }

        final MarshallerFactory factory = Marshalling.getMarshallerFactory("river", DomainServerMain.class.getClassLoader());
        final Unmarshaller unmarshaller;
        final ByteInput byteInput;
        final AsyncFuture<ServiceContainer> containerFuture;
        try {
            Module.registerURLStreamHandlerFactoryModule(Module.getBootModuleLoader().loadModule(ModuleIdentifier.create("org.jboss.vfs")));
            final MarshallingConfiguration configuration = new MarshallingConfiguration();
            configuration.setVersion(2);
            configuration.setClassResolver(new SimpleClassResolver(DomainServerMain.class.getClassLoader()));
            unmarshaller = factory.createUnmarshaller(configuration);
            byteInput = Marshalling.createByteInput(initialInput);
            unmarshaller.start(byteInput);
            final ServerTask task = unmarshaller.readObject(ServerTask.class);
            unmarshaller.finish();
            containerFuture = task.run(Arrays.<ServiceActivator>asList(new ServiceActivator() {
                @Override
                public void activate(final ServiceActivatorContext serviceActivatorContext) {
                    // TODO activate host controller client service
                }
            }));
        } catch (Throwable t) {
            t.printStackTrace(initialError);
            SystemExiter.abort(ExitCodes.FAILED);
            throw new IllegalStateException(); // not reached
        }

        Throwable caught = null;
        for (;;) {
            try {
                final String scheme = readUTFZBytes(initialInput);

                final String hostName = readUTFZBytes(initialInput);
                final int port = readInt(initialInput);
                final boolean managementSubsystemEndpoint = readBoolean(initialInput);

                final String serverAuthToken = readUTFZBytes(initialInput);
                URI hostControllerUri = new URI(scheme, null, NetworkUtils.formatPossibleIpv6Address(hostName), port, null, null, null);
                // Get the host-controller server client
                final ServiceContainer container = containerFuture.get();
                if (!container.isShutdown()) {
                    // otherwise, ServiceNotFoundException or IllegalStateException is thrown because HostControllerClient is stopped
                    final HostControllerClient client = getRequiredService(container,
                            HostControllerConnectionService.SERVICE_NAME, HostControllerClient.class);
                    // Reconnect to the host-controller
                    AuthenticationContext replacementAuthenticationContext = createAuthenticationContext(client.getServerName(), serverAuthToken);
                    latestAuthenticationContext = replacementAuthenticationContext;
                    client.reconnect(hostControllerUri, replacementAuthenticationContext, managementSubsystemEndpoint);
                }

            } catch (InterruptedIOException e) {
                Thread.interrupted();
                // ignore
            } catch (EOFException e) {
                // this means it's time to exit
                break;
            } catch (Throwable t) {
                t.printStackTrace();
                caught = t;
                break;
            }
        }

        // Once the input stream is cut off, shut down
        // We may be attempting a graceful shutdown, in which case we need to wait
        final ServiceContainer container;
        try {
            container = containerFuture.get();
            ServiceController<?> controller = container.getService(GracefulShutdownService.SERVICE_NAME);
            if(controller != null) {
                ((GracefulShutdownService)controller.getValue()).awaitSuspend();
            }
        } catch (InterruptedException ie) {
            // ignore this and exit
        } catch (Throwable  t) {
            t.printStackTrace();
        } finally {
            if (caught == null) {
                SystemExiter.logAndExit(ServerLogger.ROOT_LOGGER::shuttingDownInResponseToProcessControllerSignal, ExitCodes.NORMAL);
            } else {
                SystemExiter.abort(ExitCodes.FAILED);
            }
        }
        throw new IllegalStateException(); // not reached
    }

    /**
     * Get the latest {@code AuthenticationContext} or {@code null} if an update has not been received.
     *
     * @return the latest {@code AuthenticationContext} or {@code null} if an update has not been received.
     */
    static AuthenticationContext getLatestAuthenticationContext() {
        return latestAuthenticationContext;
    }

    static <T> T getRequiredService(final ServiceContainer container, final ServiceName serviceName, Class<T> type) {
        final ServiceController<?> controller = container.getRequiredService(serviceName);
        return type.cast(controller.getValue());
    }

}
