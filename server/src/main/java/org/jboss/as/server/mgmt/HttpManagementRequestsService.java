/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
