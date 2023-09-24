/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.protocol.mgmt.support.ManagementChannelShutdownHandle;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRequestTracker;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xnio.OptionMap;

/**
 * Abstract service responsible for listening for channel open requests.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractChannelOpenListenerService implements Service, OpenListener {

    /** How long we wait for active operations to clear before allowing channel close to proceed */
    protected static final int CHANNEL_SHUTDOWN_TIMEOUT;

    static {
        String prop = null;
        int timeout;
        try {
            prop = WildFlySecurityManager.getPropertyPrivileged("jboss.as.management.channel.close.timeout", "15000");
            timeout = Integer.parseInt(prop);
        } catch (NumberFormatException e) {
            ControllerLogger.ROOT_LOGGER.invalidChannelCloseTimeout(e, "jboss.as.management.channel.close.timeout", prop);
            timeout = 15000;
        }
        CHANNEL_SHUTDOWN_TIMEOUT = timeout;
    }

    private final Supplier<Endpoint> endpointSupplier;
    private final Supplier<ManagementChannelRegistryService> registrySupplier;

    protected final String channelName;
    private final OptionMap optionMap;
    private final Set<ManagementChannelShutdownHandle> handles = Collections.synchronizedSet(new HashSet<ManagementChannelShutdownHandle>());

    private volatile ManagementRequestTracker trackerService;
    private volatile boolean closed = true;

    public AbstractChannelOpenListenerService(final Supplier<Endpoint> endpointSupplier,
                                              final Supplier<ManagementChannelRegistryService> registrySupplier,
                                              final String channelName, final OptionMap optionMap) {
        this.endpointSupplier = endpointSupplier;
        this.registrySupplier = registrySupplier;
        this.channelName = channelName;
        this.optionMap = optionMap;
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        try {
            closed = false;
            RemotingLogger.ROOT_LOGGER.debugf("Registering channel listener for %s", channelName);
            final ManagementChannelRegistryService registry = this.registrySupplier.get();
            final Registration registration = endpointSupplier.get().registerService(channelName, this, optionMap);
            // Add to global registry
            registry.register(registration);
            trackerService = registry.getTrackerService();
        } catch (Exception e) {
            throw RemotingLogger.ROOT_LOGGER.couldNotStartChanelListener(e);
        }
    }

    @Override
    public synchronized void stop(final StopContext context) {
        closed = true;
        // Signal all mgmt services that we are shutting down
        trackerService.prepareShutdown();
        // Copy off the set to avoid ConcurrentModificationException
        final Set<ManagementChannelShutdownHandle> handlesCopy = copyHandles();
        for (final ManagementChannelShutdownHandle handle : handlesCopy) {
            handle.shutdown();
        }
        final Runnable shutdownTask = new Runnable() {
            @Override
            public void run() {
                final long end = System.currentTimeMillis() + CHANNEL_SHUTDOWN_TIMEOUT;
                boolean interrupted = Thread.currentThread().isInterrupted();
                try {
                    for (final ManagementChannelShutdownHandle handle : handlesCopy) {
                        final long remaining = end - System.currentTimeMillis();
                        try {
                            if (!interrupted && !handle.awaitCompletion(remaining, TimeUnit.MILLISECONDS)) {
                                ControllerLogger.ROOT_LOGGER.gracefulManagementChannelHandlerShutdownTimedOut(CHANNEL_SHUTDOWN_TIMEOUT);
                            }
                            trackerService.unregisterTracker(handle);
                        } catch (InterruptedException e) {
                            interrupted = true;
                            ControllerLogger.ROOT_LOGGER.gracefulManagementChannelHandlerShutdownFailed(e);
                        } catch (Exception e) {
                            ControllerLogger.ROOT_LOGGER.gracefulManagementChannelHandlerShutdownFailed(e);
                        } finally {
                            handle.shutdownNow();
                        }
                    }
                } finally {
                    context.complete();
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
        // Execute async shutdown task
        context.asynchronous();
        execute(shutdownTask);
    }

    @Override
    public void channelOpened(Channel channel) {
        // When the server/host is stopping we don't accept new connections
        // this should be using the graceful shutdown control
        if(closed) {
            RemotingLogger.ROOT_LOGGER.debugf("server shutting down, closing channel %s.", channel);
            channel.closeAsync();
            return;
        }
        final ManagementChannelShutdownHandle handle = handleChannelOpened(channel);
        trackerService.registerTracker(handle);
        handles.add(handle);
        channel.addCloseHandler(new CloseHandler<Channel>() {
            public void handleClose(final Channel closed, final IOException exception) {
                handles.remove(handle);
                handle.shutdownNow();
                trackerService.unregisterTracker(handle);
                RemotingLogger.ROOT_LOGGER.tracef("Handling close for %s", handle);
            }
        });
    }

    @Override
    public void registrationTerminated() {
        // Copy off the set to avoid ConcurrentModificationException
        final Set<ManagementChannelShutdownHandle> copy = copyHandles();
        for (final ManagementChannelShutdownHandle channel : copy) {
            channel.shutdownNow();
        }
    }

    /**
     * Handle a channel open event.
     *
     * @param channel the opened channel
     * @return the shutdown handle
     */
    protected abstract ManagementChannelShutdownHandle handleChannelOpened(Channel channel);

    /**
     * Execute the shutdown task.
     *
     * @param runnable the runnable
     */
    protected abstract void execute(Runnable runnable);

    private Set<ManagementChannelShutdownHandle> copyHandles() {
        // Must synchronize on Collections.synchronizedSet when iterating
        synchronized (handles) {
            return new HashSet<ManagementChannelShutdownHandle>(handles);
        }
    }

}
