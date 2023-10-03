/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import java.util.logging.ErrorManager;

import org.jboss.logmanager.configuration.ConfigurationResource;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ErrorManagerConfigurationImpl extends AbstractPropertyConfiguration<ErrorManager, ErrorManagerConfigurationImpl> implements ErrorManagerConfiguration {

    ErrorManagerConfigurationImpl(final LogContextConfiguration configuration, final String name, final String moduleName, final String className, final String[] constructorProperties) {
        super(ErrorManager.class, configuration, configuration.getErrorManagerConfigurations(), name, moduleName, className, constructorProperties);
    }

    String getDescription() {
        return "error manager";
    }

    @Override
    void addConfigurationResource(final ConfigurationResource<ErrorManager> resource) {
        getConfiguration().addErrorManager(getName(), resource);
    }

    @Override
    ConfigurationResource<ErrorManager> removeInstance() {
        return getConfiguration().removeErrorManager(getName());
    }

    @Override
    public ErrorManager getInstance() {
        return getConfiguration().getErrorManager(getName());
    }
}
