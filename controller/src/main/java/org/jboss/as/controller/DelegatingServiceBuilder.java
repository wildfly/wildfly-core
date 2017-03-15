/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

import java.util.Collection;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.value.Value;

/**
 * A service builder which delegates to another service builder.
 * this class is copy of {@link org.jboss.msc.service.DelegatingServiceBuilder} with exception of not being final
 *
 * @param <T> the service type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
//TODO this is just non-final version of org.jboss.msc.DelegatingServiceBuilder, we should remove it if msc fixes that
class DelegatingServiceBuilder<T> implements ServiceBuilder<T> {
    private final ServiceBuilder<T> delegate;

    /**
     * Construct a new instance.
     *
     * @param delegate the builder to delegate to
     */
    DelegatingServiceBuilder(final ServiceBuilder<T> delegate) {
        this.delegate = delegate;
    }

    protected ServiceBuilder<T> getDelegate() {
        return delegate;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addAliases(final ServiceName... aliases) {
        delegate.addAliases(aliases);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> setInitialMode(final ServiceController.Mode mode) {
        delegate.setInitialMode(mode);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependencies(final ServiceName... dependencies) {
        delegate.addDependencies(dependencies);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependencies(final DependencyType dependencyType, final ServiceName... dependencies) {
        delegate.addDependencies(dependencyType, dependencies);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependencies(final Iterable<ServiceName> dependencies) {
        delegate.addDependencies(dependencies);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependencies(final DependencyType dependencyType, final Iterable<ServiceName> dependencies) {
        delegate.addDependencies(dependencyType, dependencies);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceName dependency) {
        delegate.addDependency(dependency);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final DependencyType dependencyType, final ServiceName dependency) {
        delegate.addDependency(dependencyType, dependency);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceName dependency, final Injector<Object> target) {
        delegate.addDependency(dependency, target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final DependencyType dependencyType, final ServiceName dependency, final Injector<Object> target) {
        delegate.addDependency(dependencyType, dependency, target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public <I> ServiceBuilder<T> addDependency(final ServiceName dependency, final Class<I> type, final Injector<I> target) {
        delegate.addDependency(dependency, type, target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public <I> ServiceBuilder<T> addDependency(final DependencyType dependencyType, final ServiceName dependency, final Class<I> type, final Injector<I> target) {
        delegate.addDependency(dependencyType, dependency, type, target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public <I> ServiceBuilder<T> addInjection(final Injector<? super I> target, final I value) {
        delegate.addInjection(target, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public <I> ServiceBuilder<T> addInjectionValue(final Injector<? super I> target, final Value<I> value) {
        delegate.addInjectionValue(target, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addInjection(final Injector<? super T> target) {
        delegate.addInjection(target);
        return this;
    }

    @Override
    public ServiceBuilder<T> addMonitor(final StabilityMonitor monitor) {
        delegate.addMonitor(monitor);
        return this;
    }

    @Override
    public ServiceBuilder<T> addMonitors(final StabilityMonitor... monitors) {
        delegate.addMonitors(monitors);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addListener(final ServiceListener<? super T> listener) {
        delegate.addListener(listener);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addListener(final ServiceListener<? super T>... listeners) {
        delegate.addListener(listeners);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addListener(final Collection<? extends ServiceListener<? super T>> listeners) {
        delegate.addListener(listeners);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceController<T> install() throws ServiceRegistryException {
        return delegate.install();
    }
}
