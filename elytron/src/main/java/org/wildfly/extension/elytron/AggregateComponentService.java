/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
