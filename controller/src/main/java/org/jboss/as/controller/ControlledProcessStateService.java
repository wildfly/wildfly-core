/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.function.Consumer;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Exposes the current {@link ControlledProcessState.State} and allows services to register a listener for changes
 * to it.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ControlledProcessStateService implements Service<ControlledProcessStateService>, ProcessStateNotifier {

    /** @deprecated use the 'org.wildfly.management.process-state-notifier' capability to obtain a {@link ProcessStateNotifier}*/
    @Deprecated
    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("controlled-process-state");
    /** Only for use within the WildFly Core kernel; may change or be removed at any time */
    public static final ServiceName INTERNAL_SERVICE_NAME = AbstractControllerService.PROCESS_STATE_NOTIFIER_CAPABILITY.getCapabilityServiceName();

    @SuppressWarnings("unchecked")
    public static ServiceController<ControlledProcessStateService> addService(ServiceTarget target, ControlledProcessState processState) {
        final ControlledProcessStateService service = processState.getService();
        final ServiceBuilder<?> sb = target.addService(INTERNAL_SERVICE_NAME);
        service.serviceConsumer = sb.provides(INTERNAL_SERVICE_NAME, SERVICE_NAME);
        sb.setInstance(service);
        return (ServiceController<ControlledProcessStateService>) sb.install();
    }

    private ControlledProcessState.State processState;
    private final PropertyChangeSupport changeSupport;
    private volatile Consumer<ControlledProcessStateService> serviceConsumer;

    ControlledProcessStateService(ControlledProcessState.State initialState) {
        this.processState = initialState;
        changeSupport = new PropertyChangeSupport(this);
    }

    @Override
    public void start(final StartContext context) throws StartException {
        serviceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        serviceConsumer.accept(null);
    }

    @Override
    public ControlledProcessStateService getValue() {
        return this;
    }

    /**
     * Returns the current process state.
     *
     * @return  the current state
     */
    @Override
    public ControlledProcessState.State getCurrentState() {
        return processState;
    }

    /**
     * Registers a listener for changes to the process state.
     *
     * @param listener the listener
     */
    @Override
    public void addPropertyChangeListener(
            PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Removes a previously {@link #addPropertyChangeListener(PropertyChangeListener) registered listener}.
     *
     * @param listener the listener
     */
    @Override
    public void removePropertyChangeListener(
            PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

    synchronized void stateChanged(ControlledProcessState.State newState) {
        final ControlledProcessState.State oldState = processState;
        processState = newState;
        changeSupport.firePropertyChange("currentState", oldState, newState);
    }
}
