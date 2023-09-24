/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

import static io.undertow.predicate.Predicates.not;
import static io.undertow.predicate.Predicates.path;

/**
 * ResourceHandler for the error context.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ErrorContextHandler {

    private static final String INDEX_HTML = "index.html";
    private static final String INDEX_WIN_HTML = "index_win.html";
    private static final String ERROR_MODULE = "org.jboss.as.domain-http-error-context";

    static final String ERROR_CONTEXT = "/error";

    private static final String DEFAULT_RESOURCE;

    static {
        boolean windows = OperatingSystemDetector.INSTANCE.isWindows();
        if (windows) {
            DEFAULT_RESOURCE = "/" + INDEX_WIN_HTML;
        } else {
            DEFAULT_RESOURCE = "/" + INDEX_HTML;
        }
    }

    private ErrorContextHandler() {

    }

    public static HttpHandler createErrorContext(final String slot) throws ModuleLoadException {
        final ClassPathResourceManager cpresource = new ClassPathResourceManager(getClassLoader(Module.getCallerModuleLoader(), ERROR_MODULE, slot), "");
        final io.undertow.server.handlers.resource.ResourceHandler handler = new io.undertow.server.handlers.resource.ResourceHandler(cpresource)
                .setAllowed(not(path("META-INF")))
                .setDirectoryListingEnabled(false)
                .setCachable(Predicates.<HttpServerExchange>falsePredicate());

        //we also need to setup the default resource redirect
        return new PredicateHandler(path("/"), new RedirectHandler(ExchangeAttributes.constant(ERROR_CONTEXT + DEFAULT_RESOURCE)), handler);
    }

    private static ClassLoader getClassLoader(final ModuleLoader moduleLoader, final String module, final String slot) throws ModuleLoadException {
        ModuleIdentifier id = ModuleIdentifier.create(module, slot);

        return moduleLoader.loadModule(id).getClassLoader();
    }
}
