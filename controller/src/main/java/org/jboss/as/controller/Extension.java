/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.parsing.ExtensionParsingContext;

/**
 * An extension to the JBoss Application Server.  Implementations of this interface should
 * have a zero-arg constructor.  Extension modules must contain a {@code META-INF/services/org.jboss.as.controller.Extension}
 * file with a line containing the name of the implementation class.
 *
 * @see java.util.ServiceLoader
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Extension {

    /**
     * Initialize this extension by registering its operation handlers and configuration
     * marshaller with the given {@link ExtensionContext}.
     * <p>When this method is invoked the {@link Thread#getContextClassLoader() thread context classloader} will
     * be set to be the defining class loader of the class that implements this interface.</p>
     *
     * @param context the extension context
     */
    void initialize(ExtensionContext context);

    /**
     * Initialize the XML parsers for this extension and register them with the given {@link ExtensionParsingContext}.
     * <p>When this method is invoked the {@link Thread#getContextClassLoader() thread context classloader} will
     * be set to be the defining class loader of the class that implements this interface.</p>
     *
     * @param context the extension parsing context
     */
    void initializeParsers(ExtensionParsingContext context);
}
