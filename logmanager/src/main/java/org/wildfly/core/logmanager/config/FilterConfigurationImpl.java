/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import java.util.logging.Filter;

import org.jboss.logmanager.configuration.ConfigurationResource;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class FilterConfigurationImpl extends AbstractPropertyConfiguration<Filter, FilterConfigurationImpl> implements FilterConfiguration {

    FilterConfigurationImpl(final LogContextConfiguration configuration, final String name, final String moduleName, final String className, final String[] constructorProperties) {
        super(Filter.class, configuration, configuration.getFilterConfigurations(), name, moduleName, className, constructorProperties);
    }

    String getDescription() {
        return "filter";
    }

    @Override
    void addConfigurationResource(final ConfigurationResource<Filter> resource) {
        getConfiguration().addFilter(getName(), resource);
    }

    @Override
    ConfigurationResource<Filter> removeInstance() {
        return getConfiguration().removeFilter(getName());
    }

    @Override
    public Filter getInstance() {
        return getConfiguration().getFilter(getName());
    }
}
