/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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

package org.jboss.as.process.jdk;

import static java.lang.System.getSecurityManager;
import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class SecurityUtils {

    private SecurityUtils() {
        // forbidden instantiation
    }

    static String getSystemProperty(final String propertyName) {
        if (getSecurityManager() != null) {
            return doPrivileged(new SystemGetPropertyAction(propertyName));
        } else {
            return System.getProperty(propertyName);
        }
    }

    static String getSystemVariable(final String variableName) {
        if (getSecurityManager() != null) {
            return doPrivileged(new SystemGetenvAction(variableName));
        } else {
            return System.getenv(variableName);
        }
    }

    private static final class SystemGetPropertyAction implements PrivilegedAction<String> {
        private final String propertyName;

        private SystemGetPropertyAction(final String propertyName) {
            this.propertyName = propertyName;
        }

        public String run() {
            return System.getProperty(propertyName);
        }
    }

    private static final class SystemGetenvAction implements PrivilegedAction<String> {
        private final String propertyName;

        private SystemGetenvAction(final String propertyName) {
            this.propertyName = propertyName;
        }

        public String run() {
            return System.getenv(propertyName);
        }
    }

}
