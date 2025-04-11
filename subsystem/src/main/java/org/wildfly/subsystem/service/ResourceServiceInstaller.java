/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.jboss.as.controller.OperationContext;
import org.wildfly.common.function.Functions;

/**
 * Installs a service into the target associated with an {@link OperationContext}.
 * @author Paul Ferraro
 */
public interface ResourceServiceInstaller {

    /**
     * Installs a service into the target associated with the specified operation context.
     * @param context an operation context
     * @return a mechanism to remove the installed service
     */
    Consumer<OperationContext> install(OperationContext context);

    /**
     * An installer that installs no services.
     */
    ResourceServiceInstaller NONE = new ResourceServiceInstaller() {
        @Override
        public Consumer<OperationContext> install(OperationContext context) {
            return Functions.discardingConsumer();
        }
    };

    /**
     * Returns a composite {@link ResourceServiceInstaller} that installs the specified installers.
     * @param installers a variable number of installers
     * @return a composite installer
     */
    static ResourceServiceInstaller combine(ResourceServiceInstaller... installers) {
        return combine(List.of(installers));
    }

    /**
     * Returns a composite {@link ResourceServiceInstaller} that installs the specified installers.
     * @param installers a collection of installers
     * @return a composite installer
     */
    static ResourceServiceInstaller combine(Collection<? extends ResourceServiceInstaller> installers) {
        return !installers.isEmpty() ? new ResourceServiceInstaller() {
            @Override
            public Consumer<OperationContext> install(OperationContext context) {
                List<Consumer<OperationContext>> removers = new ArrayList<>(installers.size());
                for (ResourceServiceInstaller installer : installers) {
                    removers.add(installer.install(context));
                }
                return new Consumer<>() {
                    @Override
                    public void accept(OperationContext context) {
                        for (Consumer<OperationContext> remover : removers) {
                            remover.accept(context);
                        }
                    }
                };
            }
        } : NONE;
    }
}
