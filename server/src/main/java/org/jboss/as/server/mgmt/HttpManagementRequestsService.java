/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.mgmt;

import org.jboss.as.domain.http.server.ManagementHttpRequestProcessor;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * @author Emanuel Muckenhuber
 */
public class HttpManagementRequestsService implements Service<ManagementHttpRequestProcessor> {

    private volatile ManagementHttpRequestProcessor processor;

    public static void installService(final ServiceName name, final ServiceTarget target) {
        final HttpManagementRequestsService processor = new HttpManagementRequestsService();
        target.addService(name, processor)
                .setInitialMode(ServiceController.Mode.PASSIVE)
                .install();
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        processor = new ManagementHttpRequestProcessor();
    }

    @Override
    public synchronized void stop(StopContext context) {
        processor.shutdownNow();
    }

    @Override
    public synchronized ManagementHttpRequestProcessor getValue() throws IllegalStateException, IllegalArgumentException {
        return processor;
    }
}
