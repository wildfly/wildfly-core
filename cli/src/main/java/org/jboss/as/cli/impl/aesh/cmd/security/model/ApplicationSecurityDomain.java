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

/**
 *
 * @author jdenise@redhat.com
 */
public class ApplicationSecurityDomain {
    private final String name;
    private final String factory;
    private final String secDomain;

    ApplicationSecurityDomain(String name, String factory, String secDomain) {
        this.name = name;
        this.factory = factory;
        this.secDomain = secDomain;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the factory
     */
    public String getFactory() {
        return factory;
    }

    /**
     * @return the secDomain
     */
    public String getSecurityDomain() {
        return secDomain;
    }

}
