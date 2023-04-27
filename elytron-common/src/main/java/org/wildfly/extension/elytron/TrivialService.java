/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * A very trivial {@link Service} implementation where creation of the value type can easily be wrapped using a {@link Supplier}
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class TrivialService<T> implements Service<T>, org.jboss.msc.Service {

    private volatile ValueSupplier<T> valueSupplier;
    private final Consumer<T> valueConsumer;

    private volatile T value;

    TrivialService() {
        this.valueConsumer = null;
    }

    TrivialService(ValueSupplier<T> valueSupplier, Consumer<T> valueConsumer) {
        this.valueSupplier = checkNotNullParam("valueSupplier", valueSupplier);
        this.valueConsumer = valueConsumer;
    }

    TrivialService(ValueSupplier<T> valueSupplier) {
        this(valueSupplier, null);
    }

    void setValueSupplier(ValueSupplier<T> valueSupplier) {
        this.valueSupplier = checkNotNullParam("valueSupplier", valueSupplier);
    }

    @Override
    public void start(StartContext context) throws StartException {
        value = checkNotNullParam("valueSupplier", valueSupplier).get();
        if (valueConsumer != null) {
            valueConsumer.accept(value);
        }
    }

    @Override
    public void stop(StopContext context) {
        valueSupplier.dispose();
        value = null;
    }

    @Override
    public T getValue() throws IllegalStateException, IllegalArgumentException {
        return value;
    }

    /**
     * A supplier for the value returned by this service, the {@link #get()} methods allows for a {@link StartException} to be
     * thrown so can be used with failed mandatory service injection.
     */
    @FunctionalInterface
    interface ValueSupplier<T> {

        T get() throws StartException;

        default void dispose() {}

    }
}
