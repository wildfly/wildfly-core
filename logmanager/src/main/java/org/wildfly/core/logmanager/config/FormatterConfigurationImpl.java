/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import java.util.logging.Formatter;

import org.jboss.logmanager.configuration.ConfigurationResource;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class FormatterConfigurationImpl extends AbstractPropertyConfiguration<Formatter, FormatterConfigurationImpl> implements FormatterConfiguration {

    FormatterConfigurationImpl(final LogContextConfiguration configuration, final String name, final String moduleName, final String className, final String[] constructorProperties) {
        super(Formatter.class, configuration, configuration.getFormatterConfigurations(), name, moduleName, className, constructorProperties);
    }

    String getDescription() {
        return "formatter";
    }

    @Override
    void addConfigurationResource(final ConfigurationResource<Formatter> resource) {
        getConfiguration().addFormatter(getName(), resource);
    }

    @Override
    ConfigurationResource<Formatter> removeInstance() {
        return getConfiguration().removeFormatter(getName());
    }

    @Override
    public Formatter getInstance() {
        return getConfiguration().getFormatter(getName());
    }
}
