package org.jboss.as.remoting;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import java.util.Set;

/**
 *  @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
public class RemoteOutboundConnectionGroupService implements Service<RemoteOutboundConnectionGroupService> {

    private final Set<ServiceName> remoteOutboundConnections;

    public Set<ServiceName> getRemoteOutboundConnections() {
        return remoteOutboundConnections;
    }

    public RemoteOutboundConnectionGroupService(final Set<ServiceName> remoteOutboundConnections){
        this.remoteOutboundConnections = remoteOutboundConnections;
    }

    @Override
    public void start(StartContext startContext) throws StartException {

    }

    @Override
    public void stop(StopContext stopContext) {

    }

    @Override
    public RemoteOutboundConnectionGroupService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
}
