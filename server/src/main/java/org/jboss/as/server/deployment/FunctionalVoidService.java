/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.function.Consumer;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.ExceptionConsumer;

/**
 * Generic {@link Service} that provides no value, and delegates its start/stop to consumers.
 * @author Paul Ferraro
 */
public class FunctionalVoidService implements Service<Void> {

    private final ExceptionConsumer<StartContext, StartException> startTask;
    private final Consumer<StopContext> stopTask;

    public FunctionalVoidService(ExceptionConsumer<StartContext, StartException> startTask) {
        this(startTask, null);
    }

    public FunctionalVoidService(ExceptionConsumer<StartContext, StartException> startTask, Consumer<StopContext> stopTask) {
        this.startTask = startTask;
        this.stopTask = stopTask;
    }

    @Override
    public void start(StartContext context) throws StartException {
        if (this.startTask != null) {
            this.startTask.accept(context);
        }
    }

    @Override
    public void stop(StopContext context) {
        if (this.stopTask != null) {
            this.stopTask.accept(context);
        }
    }

    @Override
    public Void getValue() {
        return null;
    }
}
