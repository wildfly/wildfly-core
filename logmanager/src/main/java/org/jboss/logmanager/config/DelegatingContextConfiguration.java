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

package org.jboss.logmanager.config;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.configuration.ConfigurationResource;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class DelegatingContextConfiguration extends ContextConfiguration {
    private final ContextConfiguration delegate;

    public DelegatingContextConfiguration(final ContextConfiguration delegate) {
        super(delegate.getContext());
        this.delegate = delegate;
    }

    @Override
    public LogContext getContext() {
        return delegate.getContext();
    }

    @Override
    public boolean hasLogger(final String name) {
        return delegate.hasLogger(name);
    }

    @Override
    public Logger getLogger(final String name) {
        return delegate.getLogger(name);
    }

    @Override
    public Set<String> getLoggers() {
        return delegate.getLoggers();
    }

    @Override
    public ConfigurationResource<ErrorManager> addErrorManager(final String name, final Supplier<ErrorManager> errorManager) {
        return delegate.addErrorManager(name, errorManager);
    }

    @Override
    public ConfigurationResource<ErrorManager> removeErrorManager(final String name) {
        return delegate.removeErrorManager(name);
    }

    @Override
    public boolean hasErrorManager(final String name) {
        return delegate.hasErrorManager(name);
    }

    @Override
    public ErrorManager getErrorManager(final String name) {
        return delegate.getErrorManager(name);
    }

    @Override
    public Map<String, ConfigurationResource<ErrorManager>> getErrorManagers() {
        return delegate.getErrorManagers();
    }

    @Override
    public ConfigurationResource<Handler> addHandler(final String name, final Supplier<Handler> handler) {
        return delegate.addHandler(name, handler);
    }

    @Override
    public ConfigurationResource<Handler> removeHandler(final String name) {
        return delegate.removeHandler(name);
    }

    @Override
    public boolean hasHandler(final String name) {
        return delegate.hasHandler(name);
    }

    @Override
    public Handler getHandler(final String name) {
        return delegate.getHandler(name);
    }

    @Override
    public Map<String, ConfigurationResource<Handler>> getHandlers() {
        return delegate.getHandlers();
    }

    @Override
    public ConfigurationResource<Formatter> addFormatter(final String name, final Supplier<Formatter> formatter) {
        return delegate.addFormatter(name, formatter);
    }

    @Override
    public ConfigurationResource<Formatter> removeFormatter(final String name) {
        return delegate.removeFormatter(name);
    }

    @Override
    public boolean hasFormatter(final String name) {
        return delegate.hasFormatter(name);
    }

    @Override
    public Formatter getFormatter(final String name) {
        return delegate.getFormatter(name);
    }

    @Override
    public Map<String, Supplier<Formatter>> getFormatters() {
        return delegate.getFormatters();
    }

    @Override
    public ConfigurationResource<Filter> addFilter(final String name, final Supplier<Filter> filter) {
        return delegate.addFilter(name, filter);
    }

    @Override
    public ConfigurationResource<Filter> removeFilter(final String name) {
        return delegate.removeFilter(name);
    }

    @Override
    public boolean hasFilter(final String name) {
        return delegate.hasFilter(name);
    }

    @Override
    public Filter getFilter(final String name) {
        return delegate.getFilter(name);
    }

    @Override
    public Map<String, ConfigurationResource<Filter>> getFilters() {
        return delegate.getFilters();
    }

    @Override
    public ConfigurationResource<Object> addObject(final String name, final Supplier<Object> object) {
        return delegate.addObject(name, object);
    }

    @Override
    public ConfigurationResource<Object> removeObject(final String name) {
        return delegate.removeObject(name);
    }

    @Override
    public boolean hasObject(final String name) {
        return delegate.hasObject(name);
    }

    @Override
    public Object getObject(final String name) {
        return delegate.getObject(name);
    }

    @Override
    public Map<String, ConfigurationResource<Object>> getObjects() {
        return delegate.getObjects();
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
