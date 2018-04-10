/*
Copyright 2018 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import org.jboss.as.cli.Util;

/**
 * A type of authentication factory that contains all specificities for the
 * type. Currently SASL and HTTP.
 *
 * @author jdenise@redhat.com
 */
public class AuthFactorySpec {

    public static final AuthFactorySpec SASL = new AuthFactorySpec(Util.SASL_AUTHENTICATION_FACTORY,
            Util.SASL_SERVER_FACTORY, Util.CONFIGURED, "sasl", ElytronUtil.SASL_SERVER_CAPABILITY);
    public static final AuthFactorySpec HTTP = new AuthFactorySpec(Util.HTTP_AUTHENTICATION_FACTORY,
            Util.HTTP_SERVER_MECHANISM_FACTORY, Util.GLOBAL, "http", ElytronUtil.HTTP_SERVER_CAPABILITY);

    private final String resourceType;
    private final String serverType;
    private final String serverValue;
    private final String name;
    private final String capability;

    private AuthFactorySpec(String resourceType, String serverType, String serverValue, String name, String capability) {
        this.resourceType = resourceType;
        this.serverType = serverType;
        this.serverValue = serverValue;
        this.name = name;
        this.capability = capability;
    }

    /**
     * @return the resourceType
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * @return the serverType
     */
    public String getServerType() {
        return serverType;
    }

    /**
     * @return the serverValue
     */
    public String getServerValue() {
        return serverValue;
    }

    /**
     * @return the serverValue
     */
    public String getName() {
        return name;
    }

    /**
     * @return the serverValue
     */
    public String getCapability() {
        return capability;
    }
}
