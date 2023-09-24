/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron.capabilities._private;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import org.wildfly.common.function.ExceptionSupplier;

/**
 * A {@link Supplier} for obtaining {@link DirContext} instances.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface DirContextSupplier extends ExceptionSupplier<DirContext, NamingException> {

    static DirContextSupplier from(final ExceptionSupplier<DirContext, NamingException> supplier) {
        return supplier::get;
    }

}
