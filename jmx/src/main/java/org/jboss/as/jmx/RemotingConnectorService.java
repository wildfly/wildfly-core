/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

import java.io.IOException;

import javax.management.MBeanServer;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.remoting3.Endpoint;
import org.jboss.remotingjmx.RemotingConnectorServer;

/**
 * The remote connector services
 *
 * @author Stuart Douglas
 */
public class RemotingConnectorService implements Service<RemotingConnectorServer> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("jmx", "remoting-connector-ref");

    private RemotingConnectorServer server;

    private final InjectedValue<MBeanServer> mBeanServer = new InjectedValue<MBeanServer>();

    private final InjectedValue<Endpoint> endpoint = new InjectedValue<Endpoint>();

    private final String resolvedDomain;
    private final String expressionsDomain;

    /**
     * @param resolvedDomain JMX domain name for the 'resolved' model controller (can be {@code null} if the model controller is not exposed)
     * @param expressionsDomain JMX domain name for the 'expression' model controller (can be {@code null} if the model controller is not exposed)
     */
    private RemotingConnectorService(String resolvedDomain, String expressionsDomain) {
        this.resolvedDomain = resolvedDomain;
        this.expressionsDomain = expressionsDomain;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        MBeanServer forwarder = AuthorizingMBeanServer.wrap(new BlockingNotificationMBeanServer(mBeanServer.getValue(), resolvedDomain, expressionsDomain));
        server = new RemotingConnectorServer(forwarder, endpoint.getValue(), new ServerInterceptorFactory());
        try {
            server.start();
        } catch (IOException e) {
            throw new StartException(e);
        }
    }

    @Override
    public synchronized void stop(final StopContext context) {
        try {
            server.stop();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized RemotingConnectorServer getValue() throws IllegalStateException, IllegalArgumentException {
        return server;
    }

    static void addService(final ServiceTarget target,
                           final ServiceName remotingCapability,
                           final String resolvedDomain,
                           final String expressionsDomain) {
        final RemotingConnectorService service = new RemotingConnectorService(resolvedDomain, expressionsDomain);
        target.addService(SERVICE_NAME, service)
                .addDependency(MBeanServerService.SERVICE_NAME, MBeanServer.class, service.mBeanServer)
                .addDependency(remotingCapability, Endpoint.class, service.endpoint)
                .install();
    }
}
