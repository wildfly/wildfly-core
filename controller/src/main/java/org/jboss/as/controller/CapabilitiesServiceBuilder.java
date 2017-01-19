package org.jboss.as.controller;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public interface CapabilitiesServiceBuilder<T> extends ServiceBuilder<T> {

    <I> CapabilitiesServiceBuilder<T> addCapabilityRequirement(String capabilityName, String referenceName, Class<I> type, Injector<I> target);

    <I> CapabilitiesServiceBuilder<T> addCapabilityRequirement(String capabilityName, Class<I> type, Injector<I> target);

    @Override
    CapabilitiesServiceBuilder<T> setInitialMode(ServiceController.Mode mode);
}
