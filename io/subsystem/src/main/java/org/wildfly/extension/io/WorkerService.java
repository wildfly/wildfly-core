/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2013, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */

package org.wildfly.extension.io;

import java.net.InetSocketAddress;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.net.CidrAddressTable;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public class WorkerService implements Service<XnioWorker> {
    private final XnioWorker.Builder builder;
    private XnioWorker worker;
    private volatile StopContext stopContext;

    /**
     * @deprecated Use {@link #WorkerService(XnioWorker.Builder)} instead to allow setting of full range of options.
     */
    public WorkerService(OptionMap optionMap) {
        this(Xnio.getInstance().createWorkerBuilder().populateFromOptions(optionMap));
    }

    public WorkerService(XnioWorker.Builder builder) {
        this.builder = builder;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        builder.setTerminationTask(this::stopDone);
        worker = builder.build();
    }

    @Override
    public void stop(StopContext context) {
        this.stopContext = context;
        context.asynchronous();
        worker.shutdown();
        worker = null;
    }

    private void stopDone() {
        final StopContext stopContext = this.stopContext;
        this.stopContext = null;
        assert stopContext != null;
        stopContext.complete();
    }

    CidrAddressTable<InetSocketAddress> getBindingsTable() {
        return builder.getBindAddressConfigurations();
    }

    @Override
    public XnioWorker getValue() throws IllegalStateException, IllegalArgumentException {
        return worker;
    }
}
