/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.remoting;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.EndpointBuilder;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

/**
 * An MSC service for Remoting endpoints.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class EndpointService implements Service {

    protected final String endpointName;
    protected volatile Endpoint endpoint;
    protected final OptionMap optionMap;
    private final Consumer<Endpoint> endpointConsumer;
    private final Supplier<XnioWorker> workerSupplier;

    public EndpointService(final Consumer<Endpoint> endpointConsumer, final Supplier<XnioWorker> workerSupplier, String nodeName, EndpointType type, final OptionMap optionMap) {
        this.endpointConsumer = endpointConsumer;
        this.workerSupplier = workerSupplier;
        if (nodeName == null) {
            nodeName = "remote";
        }
        endpointName = type == EndpointType.SUBSYSTEM ? nodeName : nodeName + ":" + type;
        this.optionMap = optionMap;
    }

    /** {@inheritDoc} */
    public void start(final StartContext context) throws StartException {
        final Endpoint endpoint;
        final EndpointBuilder builder = Endpoint.builder();
        builder.setEndpointName(endpointName);
        builder.setXnioWorker(workerSupplier.get());
        try {
            endpoint = builder.build();
        } catch (IOException e) {
            throw RemotingLogger.ROOT_LOGGER.couldNotStart(e);
        }
        // Reuse the options for the remote connection factory for now
        this.endpoint = endpoint;
        endpointConsumer.accept(endpoint);
    }

    /** {@inheritDoc} */
    public void stop(final StopContext context) {
        context.asynchronous();
        endpointConsumer.accept(null);
        final Endpoint endpoint = this.endpoint;
        this.endpoint = null;
        try {
            endpoint.closeAsync();
        } finally {
            endpoint.addCloseHandler((closed, exception) -> {
                context.complete();
            });
        }
    }

    public enum EndpointType {
        MANAGEMENT,
        SUBSYSTEM
    }
}
