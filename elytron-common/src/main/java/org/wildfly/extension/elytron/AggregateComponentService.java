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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 *
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AggregateComponentService<T> implements Service<T> {

    private final Class<T> aggregationType;
    private final Function<T[], T> aggregator;

    private List<InjectedValue<T>> injections = new ArrayList<InjectedValue<T>>();

    private T aggregation;

    AggregateComponentService(Class<T> aggregationType, Function<T[], T> aggregator) {
        this.aggregationType = aggregationType;
        this.aggregator = aggregator;
    }

    /**
     * @see org.jboss.msc.service.Service#start(org.jboss.msc.service.StartContext)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void start(StartContext context) throws StartException {
        ArrayList<T> toAggregate = new ArrayList<T>(injections.size());
        for (InjectedValue<T> current : injections) {
            toAggregate.add(current.getValue());
        }

        aggregation = aggregator.apply(toAggregate.toArray((T[])Array.newInstance(aggregationType, toAggregate.size())));
    }

    /**
     * @see org.jboss.msc.service.Service#stop(org.jboss.msc.service.StopContext)
     */
    @Override
    public void stop(StopContext context) {
        aggregation = null;
    }

    Injector<T> newInjector() {
        InjectedValue<T> injectedValue = new InjectedValue<T>();
        injections.add(injectedValue);

        return injectedValue;
    }

    /**
     * @see org.jboss.msc.value.Value#getValue()
     */
    @Override
    public T getValue() throws IllegalStateException, IllegalArgumentException {
        return aggregation;
    }

}
