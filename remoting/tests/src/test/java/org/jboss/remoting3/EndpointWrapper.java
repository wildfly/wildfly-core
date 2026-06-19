/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.remoting3;

import java.net.URI;
import java.util.Map;

import org.xnio.OptionMap;

public class EndpointWrapper {

    public static Map<URI, OptionMap> getOptionMap(Endpoint endpoint) {
        EndpointImpl endpointImpl = (EndpointImpl) endpoint;
        return endpointImpl.getConnectionOptions();
    }

    public static OptionMap getDefaultOptionMap(Endpoint endpoint) {
        EndpointImpl endpointImpl = (EndpointImpl) endpoint;
        return endpointImpl.getDefaultConnectionOptionMap();
    }

}
