/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.function.Supplier;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.msc.Service;
import org.jboss.msc.service.DelegatingServiceBuilder;
import org.jboss.msc.service.DelegatingServiceTarget;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;

/**
 * {@link org.jboss.msc.service.ServiceTarget} whose builders support capability requirements.
 * @author Paul Ferraro
 */
public interface RequirementServiceTarget extends ServiceTarget {

    @Override
    RequirementServiceBuilder<?> addService();

    @Override
    RequirementServiceTarget addListener(LifecycleListener listener);

    @Override
    RequirementServiceTarget removeListener(LifecycleListener listener);

    @Override
    RequirementServiceTarget subTarget();

    /**
     * Wraps an MSC {@link ServiceTarget} adding capability resolution support.
     * @param target a service target
     * @param support capability resolution support
     * @return a wrapped service target
     */
    static RequirementServiceTarget forTarget(ServiceTarget target, CapabilityServiceSupport support) {
        return new CapabilityServiceSupportTarget(target, support);
    }

    class CapabilityServiceSupportTarget extends DelegatingServiceTarget implements RequirementServiceTarget {
        private final CapabilityServiceSupport support;

        CapabilityServiceSupportTarget(ServiceTarget target, CapabilityServiceSupport support) {
            super(target);
            this.support = support;
        }

        @Override
        public RequirementServiceTarget addListener(LifecycleListener listener) {
            super.addListener(listener);
            return this;
        }

        @Override
        public RequirementServiceTarget removeListener(LifecycleListener listener) {
            super.removeListener(listener);
            return this;
        }

        @Override
        public RequirementServiceTarget subTarget() {
            return new CapabilityServiceSupportTarget(super.subTarget(), this.support);
        }

        @Override
        public RequirementServiceBuilder<?> addService() {
            return new CapabilityServiceSupportBuilder<>(super.addService(), this.support);
        }
    }

    class CapabilityServiceSupportBuilder<T> extends DelegatingServiceBuilder<T> implements RequirementServiceBuilder<T> {
        private final CapabilityServiceSupport support;

        CapabilityServiceSupportBuilder(ServiceBuilder<T> builder, CapabilityServiceSupport support) {
            super(builder);
            this.support = support;
        }

        @Override
        public RequirementServiceBuilder<T> setInitialMode(ServiceController.Mode mode) {
            super.setInitialMode(mode);
            return this;
        }

        @Override
        public RequirementServiceBuilder<T> setInstance(Service service) {
            super.setInstance(service);
            return this;
        }

        @Override
        public RequirementServiceBuilder<T> addListener(LifecycleListener listener) {
            super.addListener(listener);
            return this;
        }

        @Override
        public <V> Supplier<V> requiresCapability(String capabilityName, Class<V> dependencyType, String... referenceNames) {
            return super.requires(this.support.getCapabilityServiceName(capabilityName, referenceNames));
        }
    }
}
