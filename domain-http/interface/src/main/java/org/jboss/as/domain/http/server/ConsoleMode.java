/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import static io.undertow.predicate.Predicates.not;
import static io.undertow.predicate.Predicates.path;

import java.io.File;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import org.jboss.as.domain.http.server.logging.HttpServerLogger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.wildfly.security.manager.WildFlySecurityManager;


/**
 * Different modes for showing the admin console
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public enum ConsoleMode {

    /**
     * Show the console normally
     */
    CONSOLE {

        private Boolean consoleLoadable;
        @Override
        ResourceHandlerDefinition createConsoleHandler(String slot) throws ModuleLoadException {
            consoleLoadable = null;
            try {
                return ConsoleHandler.createConsoleHandler(slot);
            } catch (ModuleLoadException e) {
                consoleLoadable = false;
                throw e;
            }
        }

        @Override
        public boolean hasConsole() {
            // We assume true until trying to create the handler shows otherwise.
            // A bit dodgy but in practice we try to create the handler before this is ever called.
            return consoleLoadable == null ? true : consoleLoadable;
        }
    },
    /**
     * If an attempt is made to go to the console show an error saying the host is a Secondary Host
     */
    SLAVE_HC {
        @Override
        ResourceHandlerDefinition createConsoleHandler(String slot) throws ModuleLoadException {
            return DisabledConsoleHandler.createNoConsoleForSecondaryHost(slot);
        }

        @Override
        public boolean hasConsole() {
            return false;
        }
    },
    /**
     * If an attempt is made to go to the console show an error saying the server/host is in admin-only mode
     */
    ADMIN_ONLY {
        @Override
        ResourceHandlerDefinition createConsoleHandler(String slot) throws ModuleLoadException {
            return DisabledConsoleHandler.createNoConsoleForAdminMode(slot);
        }

        @Override
        public boolean hasConsole() {
            return false;
        }
    },
    /**
     * If an attempt is made to go to the console a 404 is shown
     */
    NO_CONSOLE {
        @Override
        ResourceHandlerDefinition createConsoleHandler(String slot) throws ModuleLoadException {
            return null;
        }

        @Override
        public boolean hasConsole() {
            return false;
        }
    };

    /**
     * Returns a console handler for the mode
     *
     * @return the console handler, may be {@code null}
     */
    ResourceHandlerDefinition createConsoleHandler(String slot) throws ModuleLoadException {
        throw new IllegalStateException("Not overridden for " + this);
    }

    /**
     * Returns whether the console is displayed or not
     */
    public boolean hasConsole() {
        throw new IllegalStateException("Not overridden for " + this);
    }


    /**
     * An extension of the ResourceHandler to configure the handler to server up resources from the console module only.
     *
     * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
     */
    static class ConsoleHandler {

        private static final String CONSOLE_MODULE = "org.jboss.as.console";
        private static final String CONTEXT = "/console";

        static ResourceHandlerDefinition createConsoleHandler(String skin) throws ModuleLoadException {
            final ClassPathResourceManager resource = new ClassPathResourceManager(findConsoleClassLoader(Module.getCallerModuleLoader(), skin), "");
            return DomainUtil.createStaticContentHandler(resource, CONTEXT);

        }

        static ClassLoader findConsoleClassLoader(ModuleLoader moduleLoader, String consoleSkin) throws ModuleLoadException {
            final String moduleName = CONSOLE_MODULE + "." + (consoleSkin == null ? "main" : consoleSkin);

            // Find all console versions on the filesystem, sorted by version
            SortedSet<ConsoleVersion> consoleVersions = findConsoleVersions(moduleName);
            for (ConsoleVersion consoleVersion : consoleVersions) {
                try {
                    return getClassLoader(moduleLoader, moduleName, consoleVersion.getName());
                } catch (ModuleLoadException mle) {
                    // ignore
                }
            }

            // No joy. Fall back to the AS 7.1 approach where the module id is org.jboss.as.console:<skin>
            try {
                return getClassLoader(moduleLoader, CONSOLE_MODULE, consoleSkin);
            } catch (ModuleLoadException mle) {
                // ignore
            }

            throw HttpServerLogger.ROOT_LOGGER.consoleModuleNotFoundMsg(moduleName);
        }
    }

    /**
     * An extension of the ResourceHandler to configure the handler to show an error page when the console has been turned off.
     */
    static class DisabledConsoleHandler {

        private static final String ERROR_MODULE = "org.jboss.as.domain-http-error-context";
        private static final String CONTEXT = "/consoleerror";
        private static final String NO_CONSOLE_FOR_SECONDARY = "/noConsoleForSecondaryHostError.html";
        private static final String NO_CONSOLE_FOR_ADMIN_MODE = "/noConsoleForAdminModeError.html";

        static ResourceHandlerDefinition createConsoleHandler(String slot, String resource) throws ModuleLoadException {
            final ClassPathResourceManager cpresource = new ClassPathResourceManager(getClassLoader(Module.getCallerModuleLoader(), ERROR_MODULE, slot), "");
            final io.undertow.server.handlers.resource.ResourceHandler handler = new io.undertow.server.handlers.resource.ResourceHandler(cpresource)
                    .setAllowed(not(path("META-INF")))
                    .setResourceManager(cpresource)
                    .setDirectoryListingEnabled(false)
                    .setCachable(Predicates.<HttpServerExchange>falsePredicate());

            //we also need to setup the default resource redirect
            PredicateHandler predicateHandler = new PredicateHandler(path("/"), new RedirectHandler(ExchangeAttributes.constant(CONTEXT + resource)), handler);
            return new ResourceHandlerDefinition(CONTEXT, resource, predicateHandler);
        }


        static ResourceHandlerDefinition createNoConsoleForSecondaryHost(String slot) throws ModuleLoadException {
            return createConsoleHandler(slot, NO_CONSOLE_FOR_SECONDARY);
        }

        static ResourceHandlerDefinition createNoConsoleForAdminMode(String slot) throws ModuleLoadException {
            // Even though we are not going to use the console in admin-only, try and load
            // its module so we react properly if it's not even present.
            ConsoleHandler.findConsoleClassLoader(Module.getCallerModuleLoader(), slot);
            // If we got here we have a console module, so now we can set up a temporary redirect
            return createConsoleHandler(slot, NO_CONSOLE_FOR_ADMIN_MODE);
        }

    }


    /**
     * Scan filesystem looking for the slot versions of all modules with the given name.
     * Package protected to allow unit testing.
     *
     * @param moduleName the name portion of the target module's {@code ModuleIdentifier}
     * @return set of console versions, sorted from highest version to lowest
     */
    static SortedSet<ConsoleVersion> findConsoleVersions(String moduleName) {
        String path = moduleName.replace('.', '/');

        final String modulePath = WildFlySecurityManager.getPropertyPrivileged("module.path", null);
        SortedSet<ConsoleVersion> consoleVersions = new TreeSet<>();
        if (modulePath != null) {
            File[] moduleRoots = getFiles(modulePath, 0, 0);
            for (File root : moduleRoots) {
                findConsoleModules(root, path, consoleVersions);
                File layers = new File(root, "system" + File.separator + "layers");
                File[] children = layers.listFiles();
                if (children != null) {
                    for (File child : children) {
                        findConsoleModules(child, path, consoleVersions);
                    }
                }
                File addOns = new File(root, "system" + File.separator + "add-ons");
                children = addOns.listFiles();
                if (children != null) {
                    for (File child : children) {
                        findConsoleModules(child, path, consoleVersions);
                    }
                }
            }
        }
        return consoleVersions;
    }

    private static void findConsoleModules(File root, String path, Set<ConsoleVersion> consoleVersions) {
        File module = new File(root, path);
        File[] children = module.listFiles();
        if (children != null) {
            for (File child : children) {
                consoleVersions.add(new ConsoleVersion(child.getName()));
            }
        }
    }

    private static File[] getFiles(final String modulePath, final int stringIdx, final int arrayIdx) {
        final int i = modulePath.indexOf(File.pathSeparatorChar, stringIdx);
        final File[] files;
        if (i == -1) {
            files = new File[arrayIdx + 1];
            files[arrayIdx] = new File(modulePath.substring(stringIdx)).getAbsoluteFile();
        } else {
            files = getFiles(modulePath, i + 1, arrayIdx + 1);
            files[arrayIdx] = new File(modulePath.substring(stringIdx, i)).getAbsoluteFile();
        }
        return files;
    }


    protected static ClassLoader getClassLoader(final ModuleLoader moduleLoader, final String module, final String slot) throws ModuleLoadException {
        ModuleIdentifier id = ModuleIdentifier.create(module, slot);

        return moduleLoader.loadModule(id).getClassLoader();
    }
}
