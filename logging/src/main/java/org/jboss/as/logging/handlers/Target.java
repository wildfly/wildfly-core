/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.handlers;

import org.jboss.logmanager.handlers.ConsoleHandler;

/**
 * Date: 15.12.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public enum Target {

    CONSOLE {
        @Override
        public String toString() {
            return "console";
        }
    },
    SYSTEM_OUT {
        @Override
        public String toString() {
            return "System.out";
        }
    },
    SYSTEM_ERR {
        @Override
        public String toString() {
            return "System.err";
        }
    },;

    public static Target fromString(String value) {
        if ("System.out".equalsIgnoreCase(value)) {
            return SYSTEM_OUT;
        } else if ("System.err".equalsIgnoreCase(value)) {
            return SYSTEM_ERR;
        } else if ("console".equalsIgnoreCase(value)) {
            return CONSOLE;
        } else if (value.equalsIgnoreCase(ConsoleHandler.Target.SYSTEM_OUT.name())) {
            return SYSTEM_OUT;
        } else if (value.equalsIgnoreCase(ConsoleHandler.Target.SYSTEM_ERR.name())) {
            return SYSTEM_ERR;
        } else if (value.equalsIgnoreCase(ConsoleHandler.Target.CONSOLE.name())) {
            return CONSOLE;
        }
        return SYSTEM_OUT;
    }
}
