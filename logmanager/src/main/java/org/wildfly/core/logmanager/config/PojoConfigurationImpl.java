/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import org.jboss.logmanager.configuration.ConfigurationResource;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class PojoConfigurationImpl extends AbstractPropertyConfiguration<Object, PojoConfigurationImpl> implements PojoConfiguration {

    PojoConfigurationImpl(final LogContextConfiguration configuration, final String name, final String moduleName, final String className, final String[] constructorProperties) {
        super(Object.class, configuration, configuration.getPojoConfigurations(), name, moduleName, className, constructorProperties);
    }

    String getDescription() {
        return "pojo";
    }

    @Override
    void addConfigurationResource(final ConfigurationResource<Object> resource) {
        getConfiguration().addObject(getName(), resource);
    }

    @Override
    ConfigurationResource<Object> removeInstance() {
        return getConfiguration().removeObject(getName());
    }

    @Override
    public Object getInstance() {
        return getConfiguration().getObject(getName());
    }
}
