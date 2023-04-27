/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
