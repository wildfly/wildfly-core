/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.config;

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
