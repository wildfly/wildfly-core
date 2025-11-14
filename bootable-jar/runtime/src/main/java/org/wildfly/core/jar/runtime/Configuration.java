/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.runtime;

import java.util.Collections;
import java.util.List;

/**
 * An additional configuration to applies to the bootable JAR server.
 *
 */
public class Configuration {

        private final List<String> arguments;
        private final List<String> cliOperations;

        public Configuration(List<String> arguments, List<String> cliOperations) {
            this.arguments = arguments == null ? Collections.emptyList() : arguments;
            this.cliOperations = cliOperations == null ? Collections.emptyList() : cliOperations;
        }

        /**
         * @return the additional server arguments.
         */
        public List<String> getArguments() {
            return arguments;
        }

        /**
         * @return the additional CLI operations.
         */
        public List<String> getCliOperations() {
            return cliOperations;
        }
    }
