/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting.management;

import org.jboss.as.remoting.RemotingServices;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.remoting3.Registration;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * A basic registry for opened management channels.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ManagementChannelRegistryService implements Service {

    public static final ServiceName SERVICE_NAME = RemotingServices.REMOTING_BASE.append("management", "channel", "registry");
    private final ArrayList<Registration> registrations = new ArrayList<Registration>();
    private final ManagementRequestTracker trackerService = new ManagementRequestTracker();
    private final Consumer<ManagementChannelRegistryService> serviceConsumer;

    public static void addService(final ServiceTarget serviceTarget, final ServiceName endpointName) {
        final ServiceBuilder<?> sb = serviceTarget.addService(SERVICE_NAME);
        final Consumer<ManagementChannelRegistryService> serviceConsumer = sb.provides(SERVICE_NAME);
        sb.setInstance(new ManagementChannelRegistryService(serviceConsumer));
        // Make sure the endpoint service does not close all connections
        sb.requires(endpointName);
        sb.install();
    }

    protected ManagementChannelRegistryService(final Consumer<ManagementChannelRegistryService> serviceConsumer) {
        this.serviceConsumer = serviceConsumer;
    }

    @Override
    public void start(StartContext context) throws StartException {
        trackerService.reset();
        serviceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        serviceConsumer.accept(null);
        this.trackerService.stop();
        final ArrayList<Registration> registrations = this.registrations;
        for(final Registration registration : registrations) {
            registration.close();
        }
        registrations.clear();
    }

    public ManagementRequestTracker getTrackerService() {
        return trackerService;
    }

    public void register(final Registration registration) {
        registrations.add(registration);
    }

}
