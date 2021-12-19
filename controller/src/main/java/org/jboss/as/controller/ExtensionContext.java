/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;


import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jboss.as.controller.services.path.PathManager;

/**
 * The context for registering a new extension.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author David Bosschaert
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ExtensionContext {

    /**
     * The various types of contexts in which an {@link Extension} can be asked to initialize.
     */
    enum ContextType {
        /** The {@code Extension} will be used to extend the functionality of a server instance */
        SERVER,
        /** The {@code Extension} will be for use in domain-wide profiles managed by a Host Controller.*/
        DOMAIN,
        /** The {@code Extension} will be used to extend the functionality of a Host Controller */
        HOST_CONTROLLER
    }

    /**
     * Convenience variant of {@link #registerSubsystem(String, int, int, int)} that uses {@code 0}
     * as the {@code microVersion}.
     *
     * @param name the name of the subsystem
     * @param version the version of the subsystem's management interface.
     *
     * @return the {@link SubsystemRegistration}
     *
     * @throws IllegalStateException if the subsystem name has already been registered
     */
    SubsystemRegistration registerSubsystem(String name, ModelVersion version);

    /**
     * Register a new subsystem type.  The returned registration object should be used
     * to configure XML parsers, operation handlers, and other subsystem-specific constructs
     * for the new subsystem.  If the subsystem registration is deemed invalid by the time the
     * extension registration is complete, the subsystem registration will be ignored, and an
     * error message will be logged.
     * <p>
     * The new subsystem registration <em>must</em> register a handler and description for the
     * {@code add} operation at its root address.  The new subsystem registration <em>must</em> register a
     * {@code remove} operation at its root address.
     *
     * @param name the name of the subsystem
     * @param version the version of the subsystem's management interface.
     * @param deprecated mark this extension as deprecated
     *
     * @return the {@link SubsystemRegistration}
     *
     * @throws IllegalStateException if the subsystem name has already been registered
     */
    SubsystemRegistration registerSubsystem(String name, ModelVersion version, boolean deprecated);

    /**
     * Convenience variant of {@link #registerSubsystem(String, int, int, int)} that uses {@code 0}
     * as the {@code microVersion}.
     *
     * @param name the name of the subsystem
     * @param majorVersion the major version of the subsystem's management interface
     * @param minorVersion the minor version of the subsystem's management interface
     *
     * @return the {@link SubsystemRegistration}
     *
     * @throws IllegalStateException if the subsystem name has already been registered
     * @deprecated {@see #registerSubsystem(String, ModelVersion)}
     */
    @Deprecated
    SubsystemRegistration registerSubsystem(String name, int majorVersion, int minorVersion);

    /**
     * Register a new subsystem type.  The returned registration object should be used
     * to configure XML parsers, operation handlers, and other subsystem-specific constructs
     * for the new subsystem.  If the subsystem registration is deemed invalid by the time the
     * extension registration is complete, the subsystem registration will be ignored, and an
     * error message will be logged.
     * <p>
     * The new subsystem registration <em>must</em> register a handler and description for the
     * {@code add} operation at its root address.  The new subsystem registration <em>must</em> register a
     * {@code remove} operation at its root address.
     *
     * @param name the name of the subsystem
     * @param majorVersion the major version of the subsystem's management interface
     * @param minorVersion the minor version of the subsystem's management interface
     * @param microVersion the micro version of the subsystem's management interface
     *
     * @return the {@link SubsystemRegistration}
     *
     * @throws IllegalStateException if the subsystem name has already been registered
     * @deprecated {@see #registerSubsystem(String, ModelVersion)}
     */
    @Deprecated
    SubsystemRegistration registerSubsystem(String name, int majorVersion, int minorVersion, int microVersion);

    /**
     * Register a new subsystem type.  The returned registration object should be used
     * to configure XML parsers, operation handlers, and other subsystem-specific constructs
     * for the new subsystem.  If the subsystem registration is deemed invalid by the time the
     * extension registration is complete, the subsystem registration will be ignored, and an
     * error message will be logged.
     * <p>
     * The new subsystem registration <em>must</em> register a handler and description for the
     * {@code add} operation at its root address.  The new subsystem registration <em>must</em> register a
     * {@code remove} operation at its root address.
     *
     * @param name the name of the subsystem
     * @param majorVersion the major version of the subsystem's management interface
     * @param minorVersion the minor version of the subsystem's management interface
     * @param microVersion the micro version of the subsystem's management interface
     * @param deprecated mark this extension as deprecated
     *
     * @return the {@link SubsystemRegistration}
     *
     * @throws IllegalStateException if the subsystem name has already been registered
     * @deprecated {@see #registerSubsystem(String, ModelVersion, boolean)}
     */
    @Deprecated
    SubsystemRegistration registerSubsystem(String name, int majorVersion, int minorVersion, int microVersion, boolean deprecated);

    /**
     * Registers that the extension <strong>may</strong> provide an {@link ExpressionResolverExtension} if one of its
     * subsystems is appropriately configured. Calling this informs the management kernel that the resolver extension
     * may be installed at some point and provides a supplier via which the resolver extension can be obtained. It also
     * instructs the management kernel as to how to handle expression strings that might be of interest to the
     * resolver extension if it were configured.
     * <p>
     * Once this is invoked, the kernel expression resolver will begin calling the given {@code supplier} whenever it
     * needs to resolve an expression string. If it returns a non-null value, the returned {@link ExpressionResolverExtension}
     * will be invoked to ensure it is {@link ExpressionResolverExtension#initialize(OperationContext) initialized} and
     * to try and {@link ExpressionResolverExtension#resolveExpression(String, OperationContext) resolve the expression string.}
     * </p>
     * <p>
     * If the given {@code supplier} returns [@code null}, that indicates the resolver extension has not yet been
     * configured (and perhaps never will be.) The management kernel resolver will however continue to account for the
     * resolver extension in the following ways:
     * <ol>
     *     <li>It will check if any unresolved expression string matches the provided {@code expressionPatterns}. If not
     *     no further action is taken with respect to this resolver extension.</li>
     *     <li>If it does match, and the expression resolution is occurring in {@link OperationContext.Stage#MODEL}, an
     *     exception will be thrown. The effect of this is calling this method disables {@code Stage.MODEL} resolution
     *     of expressions that match the pattern. Resolving in Stage.MODEL is generally an anti-pattern though, although
     *     there are cases where it is attempted, particular with {@code system-property} resources.</li>
     *     <li>If it does match and the expression resolution is occurring <strong>after</strong> {@link OperationContext.Stage#MODEL},
     *     the behavior depends on the provided {@code requiresRuntimeResolution} parameter. If {@code true}, an
     *     exception will be thrown, as the required resolution could not be performed. If {@code false}, no
     *     resolution will be attempted and the overall resolution will continue on. Setting this to false allows
     *     expressions that match the pattern but which aren't definitely meant for handling by the resolver extension
     *     to still be processed.</li>
     * </ol>
     * </p>
     * <p>
     * Note that if multiple callers register resolver extensions, the management kernel will apply the above logic to
     * each in turn until one provides a resolved string or all have been given an opportunity to try. Only when all
     * have been given an opportunity to try will any exception resulting from one of the attempts be propagated. If none
     * provide a resolved string, but none throw an exception, the expression resolution will move on to trying
     * the core management kernel expression resolution mechanism.
     * </p>
     *
     * @param supplier function to be checked during expression resolution to determine if the resolver extension
     *                 is available. Supplier should return {@code null} if the resolver extension isn't actually configured
     *                 by a subsystem. The supplier itself cannot be {@code null}.
     * @param expressionPattern if the {@code supplier} returns {@code null}, the pattern the kernel expression resolver
     *                          should use to determine if a given expression string would be of interest to the
     *                          resolver extension if it was available.
     * @param requiresRuntimeResolution {@code true} if the kernel resolver should throw an expression resolution
     *                                  exception if the {@code supplier} returns {@code null}, the expression string
     *                                  to resolves matches the given {@code expressionPattern}, and the resolution
     *                                  is occuring outside of {@link OperationContext.Stage#MODEL}.
     */
    default void registerExpressionResolverExtension(Supplier<ExpressionResolverExtension> supplier, Pattern expressionPattern,
                                                     boolean requiresRuntimeResolution) {
        // The only actual impl of this interface in the WildFly Core / WildFly code overrides and implements this.
        // If there are custom impls out there used for tests and those tests call this method, they'll need to override.
        // The method has a default impl because if there are custom impls for tests (not likely) its reasonably likely
        // that those tests will not result in this method being called.
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the type of this context.
     * @return the context type. Will not be {@code null}
     */
    ContextType getType();

    /**
     * Gets the type of the current process.
     * @return the current process type. Will not be {@code null}
     */
    ProcessType getProcessType();

    /**
     * Gets the current running mode of the process.
     * @return the current running mode. Will not be {@code null}
     */
    RunningMode getRunningMode();



    /**
     * Gets whether it is valid for the extension to register resources, attributes or operations that do not
     * involve the persistent configuration, but rather only involve runtime services. Extensions should use this
     * method before registering such "runtime only" resources, attributes or operations. This
     * method is intended to avoid registering resources, attributes or operations on process types that
     * can not install runtime services.
     *
     * @return whether it is valid to register runtime resources, attributes, or operations.
     */
    boolean isRuntimeOnlyRegistrationValid();

    /**
     * Gets the process' {@link PathManager} if the process is a {@link ProcessType#isServer() server}; throws
     * an {@link IllegalStateException} if not.
     *
     * @return the path manager. Will not return {@code null}
     *
     * @throws IllegalStateException if the process is not a {@link ProcessType#isServer() server}
     */
    PathManager getPathManager();

    /**
     * Returns true if subsystems should register transformers. This is true if {@link #getProcessType().isHostController()} is true and the
     * process controller is the master domain controller.
     *
     * @return {@code true} if transformers should be registered
     * @deprecated Use {@link org.jboss.as.controller.transform.ExtensionTransformerRegistration}
     */
    @Deprecated
    boolean isRegisterTransformers();
}
