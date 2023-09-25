/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt;

import java.io.IOException;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public class ManagementWorkerService implements Service<XnioWorker> {
    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("serverManagement", "controller", "management", "worker");

    private final OptionMap options;
    private XnioWorker worker;
    private volatile StopContext stopContext;

    private ManagementWorkerService(OptionMap options) {
        this.options = options;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        final Xnio xnio = Xnio.getInstance();
        try {
            worker = xnio.createWorker(null,  options, this::stopDone);
        } catch (IOException e) {
            throw new StartException(e);
        }

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


    @Override
    public XnioWorker getValue() throws IllegalStateException, IllegalArgumentException {
        return worker;
    }

    public static void installService(ServiceTarget serviceTarget){
        //todo make configurable
        ManagementWorkerService service = new ManagementWorkerService(OptionMap.builder()
                            .set(Options.WORKER_IO_THREADS, 2)
                            .set(Options.WORKER_TASK_CORE_THREADS, 5)
                            .set(Options.WORKER_TASK_MAX_THREADS, 10)
                            .set(Options.TCP_NODELAY, true)
                            .set(Options.CORK, true)
                            .set(Options.WORKER_NAME, "management")
                            .getMap());

        serviceTarget.addService(SERVICE_NAME, service)
                .setInitialMode(ServiceController.Mode.ON_DEMAND) //have it on demand as it might not be needed in certain scenarios
                .install();

    }
}
