/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * Classification to apply to resources, attributes or operation to allow configuration
 * of whether they are related to "applications".
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class ApplicationTypeConfig {


    // Core configurations

    public static final ApplicationTypeConfig DEPLOYMENT = new ApplicationTypeConfig(ModelDescriptionConstants.DEPLOYMENT, true);

    private final boolean core;

    private final String subsystem;
    private final String name;
    private final boolean application;
    private volatile Boolean configuredApplication;

    public ApplicationTypeConfig(String subsystem, String name) {
        this(subsystem, name, false);
    }

    public ApplicationTypeConfig(String subsystem, String name, boolean application) {
        assert subsystem != null : "subsystem is null";
        assert name != null : "name is null";
        this.subsystem = subsystem;
        this.name = name;
        this.application = application;
        this.core = false;
    }

    private ApplicationTypeConfig(String name, boolean application) {
        this.core = true;
        this.subsystem = null;
        this.name = name;
        this.application = application;
    }


    public boolean isCore() {
        return core;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public String getName() {
        return name;
    }

    public boolean isDefaultApplication() {
        return application;
    }

    public Boolean getConfiguredApplication() {
        return configuredApplication;
    }

    public boolean isApplicationType() {
        final Boolean app = configuredApplication;
        return app == null ? application : app;
    }

    public void setConfiguredApplication(Boolean configuredApplication) {
        this.configuredApplication = configuredApplication;
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ApplicationTypeConfig
                && getKey().equals(((ApplicationTypeConfig)obj).getKey());
    }

    Key getKey() {
        return new Key();
    }

    boolean isCompatibleWith(ApplicationTypeConfig other) {
        return !equals(other) || application == other.application;
    }

    class Key {

        private final ApplicationTypeConfig typeConfig = ApplicationTypeConfig.this;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Key thatKey = (Key) o;
            ApplicationTypeConfig that = thatKey.typeConfig;

            return core == that.core && name.equals(that.name)
                    && !(subsystem != null ? !subsystem.equals(that.subsystem) : that.subsystem != null);

        }

        @Override
        public int hashCode() {
            int result = (core ? 1 : 0);
            result = 31 * result + (subsystem != null ? subsystem.hashCode() : 0);
            result = 31 * result + name.hashCode();
            return result;
        }

    }


}
