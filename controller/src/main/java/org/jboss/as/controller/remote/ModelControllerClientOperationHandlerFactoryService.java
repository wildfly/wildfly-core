/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.remote;

import org.jboss.as.controller.ModelController;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.remoting3.Channel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Service used to create a new client protocol operation handler per channel
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ModelControllerClientOperationHandlerFactoryService extends AbstractModelControllerOperationHandlerFactoryService {

    public ModelControllerClientOperationHandlerFactoryService(
            final Consumer<AbstractModelControllerOperationHandlerFactoryService> serviceConsumer,
            final Supplier<ModelController> modelControllerSupplier,
            final Supplier<ExecutorService> executorSupplier,
            final Supplier<ScheduledExecutorService> scheduledExecutorSupplier
    ) {
        super(serviceConsumer, modelControllerSupplier, executorSupplier, scheduledExecutorSupplier);
    }

    @Override
    public ManagementChannelHandler startReceiving(Channel channel) {
        final ManagementChannelHandler handler = new ManagementChannelHandler(ManagementClientChannelStrategy.create(channel),
                getExecutor());

        handler.addHandlerFactory(new ModelControllerClientOperationHandler(getController(), handler,
                getResponseAttachmentSupport(), getClientRequestExecutor(), channel.getConnection().getLocalIdentity()));

        channel.receiveMessage(handler.getReceiver());
        return handler;
    }
}
