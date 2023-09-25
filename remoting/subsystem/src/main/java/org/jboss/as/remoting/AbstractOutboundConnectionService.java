/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.network.OutboundConnection;
import org.jboss.msc.service.ServiceName;

/**
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractOutboundConnectionService implements OutboundConnection {

    /**
     * @deprecated Use {@code AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY}
     * capability to get the base service name for an OutboundConnection.
     */
    @Deprecated
    public static final ServiceName OUTBOUND_CONNECTION_BASE_SERVICE_NAME = RemotingServices.SUBSYSTEM_ENDPOINT.append("outbound-connection");

}
