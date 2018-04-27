/*
Copyright 2018 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.wildfly.core.embedded;

import static java.lang.System.getProperty;
import static java.lang.System.getSecurityManager;
import static java.lang.System.setProperty;
import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;

/**
 * Privileged actions used by more than one class in this module.
 *
 * @author Brian Stansberry
 */
final class SecurityActions {

    static String getPropertyPrivileged(final String name, final String def) {
        final SecurityManager sm = getSecurityManager();
        if (sm == null) {
            return getProperty(name, def);
        }
        return doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return getProperty(name, def);
            }
        });
    }

    static void setPropertyPrivileged(final String name, final String value) {
        final SecurityManager sm = getSecurityManager();
        if (sm == null) {
            setProperty(name, value);
        } else {
            doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    setProperty(name, value);
                    return null;
                }
            });
        }
    }
}
