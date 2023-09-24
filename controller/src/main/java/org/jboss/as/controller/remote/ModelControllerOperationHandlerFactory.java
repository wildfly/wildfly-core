/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.remote;

import org.jboss.as.controller.ModelController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Factory for operation handler services.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ModelControllerOperationHandlerFactory {
    AbstractModelControllerOperationHandlerFactoryService newInstance(
            final Consumer<AbstractModelControllerOperationHandlerFactoryService> serviceConsumer,
            final Supplier<ModelController> modelControllerSupplier,
            final Supplier<ExecutorService> executorSupplier,
            final Supplier<ScheduledExecutorService> scheduledExecutorSupplier
    );
}
