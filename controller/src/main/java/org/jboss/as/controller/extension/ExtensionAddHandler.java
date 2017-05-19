/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.controller.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleNotFoundException;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Base handler for the extension resource add operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ExtensionAddHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = ADD;

    private final ExtensionRegistry extensionRegistry;
    private final boolean parallelBoot;
    private final ExtensionRegistryType extensionRegistryType;
    /**
     * For a server or domain.xml extension, this will be the root resource registration.<br/>
     * For a host.xml extension, this will be the host resource registration
     */
    private final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider;

    /**
     * Create the AbstractAddExtensionHandler
     * @param extensionRegistry registry for extensions
     * @param parallelBoot {@code true} if parallel initialization of extensions during boot is possible; {@code false} if not
     * @param extensionRegistryType {@code true} if this handler will execute in a master HostController
     * @param rootResourceRegistrationProvider provides access to the root {@code ManagementResourceRegistration}
     */
    public ExtensionAddHandler(final ExtensionRegistry extensionRegistry, final boolean parallelBoot,
                               ExtensionRegistryType extensionRegistryType,
                               final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider) {
        assert extensionRegistry != null : "extensionRegistry is null";
        this.extensionRegistry = extensionRegistry;
        this.parallelBoot = parallelBoot;
        this.extensionRegistryType = extensionRegistryType;
        this.rootResourceRegistrationProvider = rootResourceRegistrationProvider;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String moduleName = context.getCurrentAddressValue();
        final ExtensionResource resource = new ExtensionResource(moduleName, extensionRegistry);

        context.addResource(PathAddress.EMPTY_ADDRESS, resource);

        final boolean install = !parallelBoot || !context.isBooting();
        final ManagementResourceRegistration rootRegistration;
        if (install) {
            rootRegistration = rootResourceRegistrationProvider.getRootResourceRegistrationForUpdate(context);
            initializeExtension(extensionRegistry, moduleName, rootRegistration, extensionRegistryType);
            if (extensionRegistryType == ExtensionRegistryType.SLAVE && !context.isBooting()) {
                ModelNode subsystems = new ModelNode();
                extensionRegistry.recordSubsystemVersions(moduleName, subsystems);
                context.getResult().set(subsystems);
            }
        } else {
            rootRegistration = null;
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                if (install) {
                    extensionRegistry.removeExtension(context.readResourceFromRoot(rootRegistration.getPathAddress()), moduleName, rootRegistration);
                }
            }
        });
    }

    void initializeExtension(String module, ManagementResourceRegistration rootRegistration) {
        initializeExtension(extensionRegistry, module, rootRegistration, extensionRegistryType);
    }

    /**
     * Initialise an extension module's extensions in the extension registry
     *
     * @param extensionRegistry the extension registry
     * @param module the name of the module containing the extensions
     * @param rootRegistration The parent registration of the extensions. For a server or domain.xml extension, this will be the root resource registration. For a host.xml extension, this will be the host resource registration
     * @param extensionRegistryType The type of the registry
     */
    static void initializeExtension(ExtensionRegistry extensionRegistry, String module,
                                    ManagementResourceRegistration rootRegistration,
                                    ExtensionRegistryType extensionRegistryType) {
        try {
            boolean unknownModule = false;
            boolean initialized = false;
            for (Extension extension : Module.loadServiceFromCallerModuleLoader(module, Extension.class)) {
                ClassLoader oldTccl = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(extension.getClass());
                try {
                    if (unknownModule || !extensionRegistry.getExtensionModuleNames().contains(module)) {
                        // This extension wasn't handled by the standalone.xml or domain.xml parsing logic, so we
                        // need to initialize its parsers so we can display what XML namespaces it supports
                        extensionRegistry.initializeParsers(extension, module, null);
                        // AS7-6190 - ensure we initialize parsers for other extensions from this module
                        // now that we know the registry was unaware of the module
                        unknownModule = true;
                    }
                    extension.initialize(extensionRegistry.getExtensionContext(module, rootRegistration, extensionRegistryType));
                } finally {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
                }
                initialized = true;
            }
            if (!initialized) {
                throw ControllerLogger.ROOT_LOGGER.notFound("META-INF/services/", Extension.class.getName(), module);
            }
        } catch (ModuleNotFoundException e) {
            // Treat this as a user mistake, e.g. incorrect module name.
            // Throw OFE so post-boot it only gets logged at DEBUG.
            throw ControllerLogger.ROOT_LOGGER.extensionModuleNotFound(e, module);
        } catch (ModuleLoadException e) {
            // The module is there but can't be loaded. Treat this as an internal problem.
            // Throw a runtime exception so it always gets logged at ERROR in the server log with stack trace details.
            throw ControllerLogger.ROOT_LOGGER.extensionModuleLoadingFailure(e, module);
        }
    }

}
