/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import java.util.Map;

import org.jboss.logmanager.configuration.ConfigurationResource;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class AbstractBasicConfiguration<T, C extends AbstractBasicConfiguration<T, C>> implements NamedConfigurable {

    private final LogContextConfiguration configuration;
    private final String name;
    private boolean removed;
    protected final Map<String, C> configs;

    AbstractBasicConfiguration(final String name, final LogContextConfiguration configuration, final Map<String, C> configs) {
        this.name = name;
        this.configuration = configuration;
        this.configs = configs;
    }

    public String getName() {
        return name;
    }

    void clearRemoved() {
        removed = false;
    }

    void setRemoved() {
        removed = true;
    }

    boolean isRemoved() {
        return removed;
    }

    abstract ConfigurationResource<T> removeInstance();

    LogContextConfiguration getConfiguration() {
        return configuration;
    }

    ConfigAction<Void> getRemoveAction() {
        return new ConfigAction<>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
                final ConfigurationResource<T> resource = removeInstance();
                if (resource != null) {
                    try {
                        resource.close();
                    } catch (Exception e) {
                        // TODO (jrp) what should we do here?
                        //throw new RuntimeException(e);
                    }
                }
            }

            public void applyPostCreate(final Void param) {
            }

            @SuppressWarnings({"unchecked"})
            public void rollback() {
                configs.put(name, (C) AbstractBasicConfiguration.this);
                clearRemoved();
            }
        };
    }

    Map<String, C> getConfigs() {
        return configs;
    }
}
