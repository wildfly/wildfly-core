/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.util.List;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 * An internal utility class to do common tasks related to check module alias when they are supplied as deployment dependencies.
 */
public class ModuleAliasChecker {

    public enum MessageContext {
        JBOSS_DEPLOYMENT_STRUCTURE_CONTEXT("jboss-deployment-structure.xml"), MANIFEST_CONTEXT("MANIFEST.MF");

        private final String context;

        MessageContext(String context) {
            this.context = context;
        }

        @Override
        public String toString() {
            return context;
        }
    }

    /**
     * Returns the target module of a given module identifier if the identifier represents a module alias.
     *
     * @param identifier A module identifier
     * @return The target module identifier of this alias or {@code null} if this identifier does not represent an alias.
     */
    static String getTargetModule(String identifier) {
        if (identifier.startsWith("javax.") && identifier.contains(".api")) {
            final ModuleLoader moduleLoader = Module.getBootModuleLoader();
            try {
                Module module = moduleLoader.loadModule(identifier);
                String moduleName = module.getName();
                if (!moduleName.equals(identifier)) {
                    return moduleName;
                }
            } catch (ModuleLoadException | RuntimeException e) {
                // Explicitly ignored, we are not interested in knowing whether the module cannot be loaded at this point.
            }
        }
        return null;
    }

    /**
     * Check whether the identifier passed as an argument represents an Alias and if so, log a warning describing that the alias
     * can be replaced with its target module.
     *
     * @param dependencies List of module dependencies we want to verify.
     * @param context The context to use in the log message to give more information about from where this module identifier has
     *        been requested. Possible values are "jboss-deployment-structure" or "manifest"
     * @param deploymentName Deployment name where the dependencies are meant to be.
     */
    public static void checkModuleAliasesForDependencies(List<ModuleDependency> dependencies, MessageContext context, String deploymentName) {
        for (ModuleDependency dependency : dependencies) {
            String identifier = dependency.getDependencyModule();
            checkModuleAlias(context, deploymentName, identifier, false);
        }
    }

     /**
     * Check whether the identifier passed as an argument represents an Alias and if so, log a warning describing that the alias
     * can be replaced with its target module.
     *
     * @param identifiers List of identifiers we want to verify.
     * @param context The context to use in the log message to give more information about from where this module identifier has
     * @param deploymentName Deployment name where the dependencies are meant to be.
     */
    public static void checkModuleAliasesForExclusions(List<ModuleIdentifier> identifiers, MessageContext context, String deploymentName) {
        for (ModuleIdentifier identifier : identifiers) {
            checkModuleAlias(context, deploymentName, identifier.toString(), true);
        }
    }

    private static void checkModuleAlias(MessageContext context, String deploymentName, String identifier, boolean exclusions) {
        String targetModule = getTargetModule(identifier);
        if (targetModule != null) {
            if (exclusions) {
                ServerLogger.DEPLOYMENT_LOGGER.aliasAddedAsExclusion(identifier, deploymentName, context.toString(), targetModule.toString());
            } else {
                ServerLogger.DEPLOYMENT_LOGGER.aliasAddedAsDependency(identifier, deploymentName, context.toString(), targetModule.toString());
            }
        }
    }
}
