package org.jboss.as.controller.services;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */

import java.util.Collection;
import java.util.Set;

import org.jboss.msc.service.BatchServiceTarget;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.value.Value;

/**
 * An "insulated" view of a service target which prevents access to other public methods on the delegate target object.
 *
 * this class is copy of {@link org.jboss.msc.service.DelegatingServiceBuilder} with exception of not being final
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class DelegatingServiceTarget implements ServiceTarget {
    private final ServiceTarget delegate;

    /**
     * Construct a new instance.
     *
     * @param delegate the delegate service target
     */
    public DelegatingServiceTarget(final ServiceTarget delegate) {
        this.delegate = delegate;
    }

    /** {@inheritDoc} */
    public <T> ServiceBuilder<T> addServiceValue(final ServiceName name, final Value<? extends Service<T>> value) throws IllegalArgumentException {
        return delegate.addServiceValue(name, value);
    }

    /** {@inheritDoc} */
    public <T> ServiceBuilder<T> addService(final ServiceName name, final Service<T> service) throws IllegalArgumentException {
        return delegate.addService(name, service);
    }

    /** {@inheritDoc} */
    public ServiceTarget addListener(final ServiceListener<Object> listener) {
        delegate.addListener(listener);
        return this;
    }

    /** {@inheritDoc} */
    public ServiceTarget addListener(final ServiceListener<Object>... listeners) {
        delegate.addListener(listeners);
        return this;
    }

    /** {@inheritDoc} */
    public ServiceTarget addListener(final Collection<ServiceListener<Object>> listeners) {
        delegate.addListener(listeners);
        return this;
    }

    /** {@inheritDoc} */
    public ServiceTarget removeListener(final ServiceListener<Object> listener) {
        delegate.removeListener(listener);
        return this;
    }

    /** {@inheritDoc} */
    public Set<ServiceListener<Object>> getListeners() {
        return delegate.getListeners();
    }

    /** {@inheritDoc} */
    public ServiceTarget addDependency(final ServiceName dependency) {
        delegate.addDependency(dependency);
        return this;
    }

    /** {@inheritDoc} */
    public ServiceTarget addDependency(final ServiceName... dependencies) {
        delegate.addDependency(dependencies);
        return this;
    }

    /** {@inheritDoc} */
    public ServiceTarget addDependency(final Collection<ServiceName> dependencies) {
        delegate.addDependency(dependencies);
        return this;
    }

    /** {@inheritDoc} */
    public ServiceTarget removeDependency(final ServiceName dependency) {
        delegate.removeDependency(dependency);
        return this;
    }

    /** {@inheritDoc} */
    public Set<ServiceName> getDependencies() {
        return delegate.getDependencies();
    }

    /** {@inheritDoc} */
    public ServiceTarget subTarget() {
        return delegate.subTarget();
    }

    /** {@inheritDoc} */
    public BatchServiceTarget batchTarget() {
        return delegate.batchTarget();
    }

    /** {@inheritDoc} */
    public ServiceTarget addMonitor(StabilityMonitor monitor) {
        return delegate.addMonitor(monitor);
    }

    /** {@inheritDoc} */
    public ServiceTarget addMonitors(StabilityMonitor... monitors) {
        return delegate.addMonitors(monitors);
    }

    /** {@inheritDoc} */
    public ServiceTarget removeMonitor(StabilityMonitor monitor) {
        return delegate.removeMonitor(monitor);
    }

    /** {@inheritDoc} */
    public Set<StabilityMonitor> getMonitors() {
        return delegate.getMonitors();
    }
}

