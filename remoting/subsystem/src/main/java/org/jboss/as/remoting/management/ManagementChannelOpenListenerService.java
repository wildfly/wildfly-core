/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
