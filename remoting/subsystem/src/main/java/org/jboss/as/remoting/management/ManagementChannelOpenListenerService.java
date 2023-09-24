/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting.management;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.jboss.as.protocol.mgmt.support.ManagementChannelInitialization;
import org.jboss.as.protocol.mgmt.support.ManagementChannelShutdownHandle;
import org.jboss.as.remoting.AbstractChannelOpenListenerService;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

/**
 * Service responsible for listening for channel open requests and associating the channel with a channel receiver
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class ManagementChannelOpenListenerService extends AbstractChannelOpenListenerService {

    private final Supplier<ManagementChannelInitialization> operationHandlerFactorySupplier;
    private final Supplier<ExecutorService> executorServiceSupplier;

    ManagementChannelOpenListenerService(final Supplier<ManagementChannelInitialization> operationHandlerFactorySupplier,
                                         final Supplier<ExecutorService> executorServiceSupplier,
                                         final Supplier<Endpoint> endpointSupplier,
                                         final Supplier<ManagementChannelRegistryService> registrySupplier,
                                         final String channelName, final OptionMap optionMap) {
        super(endpointSupplier, registrySupplier, channelName, optionMap);
        this.operationHandlerFactorySupplier = operationHandlerFactorySupplier;
        this.executorServiceSupplier = executorServiceSupplier;
    }

    @Override
    protected ManagementChannelShutdownHandle handleChannelOpened(final Channel channel) {
        final ManagementChannelInitialization initialization = operationHandlerFactorySupplier.get();
        RemotingLogger.ROOT_LOGGER.tracef("Opened %s: %s with handler %s", channelName, channel, initialization);
        return initialization.startReceiving(channel);
    }

    @Override
    protected void execute(final Runnable runnable) {
        executorServiceSupplier.get().execute(runnable);
    }

}
