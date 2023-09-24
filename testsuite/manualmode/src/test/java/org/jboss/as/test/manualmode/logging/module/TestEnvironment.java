/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.jboss.as.test.manualmode.logging.module.api.PropertyResolver;
import org.jboss.as.test.manualmode.logging.module.api.Reflection;
import org.jboss.as.test.manualmode.logging.module.impl.AppendingFileHandler;
import org.jboss.as.test.manualmode.logging.module.impl.SystemPropertyResolver;
import org.jboss.as.test.module.util.ModuleBuilder;
import org.jboss.as.test.module.util.ModuleDependency;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class TestEnvironment {
    static final String API_MODULE_NAME = "org.wildfly.test.logging.api";
    static final String IMPL_MODULE_NAME = "org.wildfly.test.logging.impl";
    private static final Queue<Runnable> CLEANUP_MODULES = new LinkedBlockingQueue<>();

    static void createModules() {
        if (CLEANUP_MODULES.isEmpty()) {
            // Create a module
            CLEANUP_MODULES.add(ModuleBuilder.of(TestEnvironment.API_MODULE_NAME, "logging-api.jar")
                    .addPackage(Reflection.class.getPackage())
                    .addDependencies("java.logging")
                    .build());
            CLEANUP_MODULES.add(ModuleBuilder.of(TestEnvironment.IMPL_MODULE_NAME, "logging-impl.jar")
                    .addPackage(AppendingFileHandler.class.getPackage())
                    .addDependencies(ModuleDependency.of("org.wildfly.common"), ModuleDependency.of("java.logging"), ModuleDependency.of("org.jboss.logmanager"),
                            ModuleDependency.of(TestEnvironment.API_MODULE_NAME))
                    .addServiceProvider(PropertyResolver.class, SystemPropertyResolver.class)
                    .build());
        }
    }

    static void deleteModules() {
        Runnable task;
        while ((task = CLEANUP_MODULES.poll()) != null) {
            task.run();
        }
    }
}
