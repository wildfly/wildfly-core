/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.domain.http.server;

import java.io.File;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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

import static io.undertow.predicate.Predicates.not;
import static io.undertow.predicate.Predicates.path;
import static io.undertow.predicate.Predicates.suffixes;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.impl.CachedAuthenticatedSessionMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.session.SessionAttachmentHandler;
import java.util.ArrayList;
import java.util.List;
import org.jboss.as.domain.http.server.security.AuthenticationMechanismWrapper;
import org.jboss.as.domain.http.server.security.RealmIdentityManager;
import org.jboss.as.domain.http.server.security.keycloak.KeycloakConfig;
import org.jboss.as.domain.http.server.security.keycloak.KeycloakAuthenticationMechanism;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.undertow.UndertowAuthenticatedActionsHandler;
import org.keycloak.adapters.undertow.UndertowUserSessionManagement;


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
        @Override
        ResourceHandlerDefinition createConsoleHandler(String slot, SecurityRealm securityRealm) throws ModuleLoadException {
            return ConsoleHandler.createConsoleHandler(slot, securityRealm);
        }

        @Override
        public boolean hasConsole() {
            return true;
        }
    },
    /**
     * If an attempt is made to go to the console show an error saying the host is a slave
     */
    SLAVE_HC {
        @Override
        ResourceHandlerDefinition createConsoleHandler(String slot, SecurityRealm securityRealm) throws ModuleLoadException {
            return DisabledConsoleHandler.createNoConsoleForSlave(slot);
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
        ResourceHandlerDefinition createConsoleHandler(String slot, SecurityRealm securityRealm) throws ModuleLoadException {
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
        ResourceHandlerDefinition createConsoleHandler(String slot, SecurityRealm securityRealm) throws ModuleLoadException {
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
    ResourceHandlerDefinition createConsoleHandler(String slot, SecurityRealm securityRealm) throws ModuleLoadException {
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
    public static class ConsoleHandler {

        private static final String NOCACHE_JS = ".nocache.js";
        private static final String INDEX_HTML = "index.html";
        private static final String APP_HTML = "App.html";

        private static final String CONSOLE_MODULE = "org.jboss.as.console";
        public static final String CONTEXT = "/console";
        private static final String DEFAULT_RESOURCE = "/" + INDEX_HTML;

        static ResourceHandlerDefinition createConsoleHandler(String skin, SecurityRealm securityRealm) throws ModuleLoadException {
            final ClassPathResourceManager resource = new ClassPathResourceManager(findConsoleClassLoader(Module.getCallerModuleLoader(), skin), "");
            final io.undertow.server.handlers.resource.ResourceHandler handler = new io.undertow.server.handlers.resource.ResourceHandler()
                    .setCacheTime(60 * 60 * 24 * 31)
                    .setAllowed(not(path("META-INF")))
                    .setResourceManager(resource)
                    .setDirectoryListingEnabled(false)
                    .setCachable(not(suffixes(NOCACHE_JS, APP_HTML, INDEX_HTML)));

            //we also need to setup the default resource redirect
            PredicateHandler predicateHandler = new PredicateHandler(path("/"), new RedirectHandler(CONTEXT + DEFAULT_RESOURCE), handler);
            if (ManagementHttpServer.usingKeycloak(securityRealm)) {
                return new ResourceHandlerDefinition(CONTEXT, DEFAULT_RESOURCE, secureConsoleAccessWithKeycloak(predicateHandler, securityRealm));
            } else {
                return new ResourceHandlerDefinition(CONTEXT, DEFAULT_RESOURCE, predicateHandler);
            }
        }

        //TODO: Refactor this.  Lots of common code between this and ManagementHttpServer.secureConsoleAccess.
        static SecurityInitialHandler secureConsoleAccessWithKeycloak(HttpHandler current, SecurityRealm securityRealm) {
            KeycloakDeployment webConsoleDeployment = KeycloakConfig.WEB_CONSOLE.deployment();
            UndertowUserSessionManagement userSessionManagement = KeycloakConfig.keycloakSessionManagement();

            List<AuthenticationMechanism> authMechanisms = new ArrayList<AuthenticationMechanism>();
            authMechanisms.add(new AuthenticationMechanismWrapper(new CachedAuthenticatedSessionMechanism(), null));
            authMechanisms.add(new AuthenticationMechanismWrapper(new KeycloakAuthenticationMechanism(webConsoleDeployment, userSessionManagement), AuthMechanism.KEYCLOAK));

            current = new AuthenticationCallHandler(current);
            current = new AuthenticationConstraintHandler(current);
            current = new AuthenticationMechanismsHandler(current, authMechanisms);
            current = new UndertowAuthenticatedActionsHandler(KeycloakConfig.WEB_CONSOLE.context(), current);
            current = new SessionAttachmentHandler(current, KeycloakConfig.sessionManager(), KeycloakConfig.sessionConfig());

            return new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, new RealmIdentityManager(securityRealm), current);
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
        private static final String NO_CONSOLE_FOR_SLAVE = "/noConsoleForSlaveDcError.html";
        private static final String NO_CONSOLE_FOR_ADMIN_MODE = "/noConsoleForAdminModeError.html";

        static ResourceHandlerDefinition createConsoleHandler(String slot, String resource) throws ModuleLoadException {
            final ClassPathResourceManager cpresource = new ClassPathResourceManager(getClassLoader(Module.getCallerModuleLoader(), ERROR_MODULE, slot), "");
            final io.undertow.server.handlers.resource.ResourceHandler handler = new io.undertow.server.handlers.resource.ResourceHandler()
                    .setAllowed(not(path("META-INF")))
                    .setResourceManager(cpresource)
                    .setDirectoryListingEnabled(false)
                    .setCachable(Predicates.<HttpServerExchange>falsePredicate());

            //we also need to setup the default resource redirect
            PredicateHandler predicateHandler = new PredicateHandler(path("/"), new RedirectHandler(CONTEXT + resource), handler);
            return new ResourceHandlerDefinition(CONTEXT, resource, predicateHandler);
        }


        static ResourceHandlerDefinition createNoConsoleForSlave(String slot) throws ModuleLoadException {
            return createConsoleHandler(slot, NO_CONSOLE_FOR_SLAVE);
        }

        static ResourceHandlerDefinition createNoConsoleForAdminMode(String slot) throws ModuleLoadException {
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
        File[] moduleRoots = getFiles(modulePath, 0, 0);
        SortedSet<ConsoleVersion> consoleVersions = new TreeSet<ConsoleVersion>();
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
        ClassLoader cl = moduleLoader.loadModule(id).getClassLoader();

        return cl;
    }
}
