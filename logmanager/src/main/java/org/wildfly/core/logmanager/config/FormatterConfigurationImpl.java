/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
