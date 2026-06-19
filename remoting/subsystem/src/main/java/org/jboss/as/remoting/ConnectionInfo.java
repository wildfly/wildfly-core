/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import java.net.URI;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.xnio.OptionMap;

/**
 * Provides connection information for an outbound remoting connection.
 */
public interface ConnectionInfo {

    NullaryServiceDescriptor<ConnectionInfo> CONNECTION_INFO_CAPABILITY = NullaryServiceDescriptor.of("org.wildfly.remoting.connection-info", ConnectionInfo.class);

    URI getDestinationUri();

    String getUsername();

    OptionMap getConnectionCreationOptions();
}
