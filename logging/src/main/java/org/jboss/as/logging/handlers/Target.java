/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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
