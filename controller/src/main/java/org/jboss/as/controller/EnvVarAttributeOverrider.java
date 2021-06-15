/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller;

import org.jboss.as.controller.logging.ControllerLogger;

/**
 * Utility class to override the value of an attribute by setting an env var corresponding to the
 * address of the resource and the name of the attribute to override.
 */
public class EnvVarAttributeOverrider {

    private static final boolean ENABLED = System.getenv().containsKey("WILDFLY_OVERRIDING_ENV_VARS");

    /**
     * Overriding the value of a management attribute with an environment variable is enabled only
     * when the {@code WILDFLY_OVERRIDING_ENV_VARS} env var is present (its value does not matter).
     */
    static boolean isEnabled() {
        return ENABLED;
    }

    static String getOverriddenValueFromEnvVar(final PathAddress address, final String attributeName) {
        // check if there is an env var that overrides the attribute value
        String envVar = replaceNonAlphanumericByUnderscoreAndMakeUpperCase(address, attributeName);
        String envVarValue = System.getenv(envVar);
        if (envVarValue != null) {
            ControllerLogger.ROOT_LOGGER.debugf("The value of the '%s' attribute of the '%s' resource is set by the environment variable '%s'",
                    attributeName, address, envVar);
            return envVarValue;
        }
        return null;
    }

    static String replaceNonAlphanumericByUnderscoreAndMakeUpperCase(final PathAddress address, final String attributeName) {
        // we use 2 underscores to separate the address from the attribute name:
        // 1. for readability
        // 2. to avoid any collusion
        //   attribute b-c on /resource=a => RESOURCE_A__B_C
        //   attribute c on /resource=a-b => RESOURCE_A_B__C
        String name = address.toCLIStyleString().substring(1) + "__" + attributeName;
        int length = name.length();
        StringBuilder sb = new StringBuilder();
        int c;
        for (int i = 0; i < length; i += Character.charCount(c)) {
            c = Character.toUpperCase(name.codePointAt(i));
            if ('A' <= c && c <= 'Z' ||
                    '0' <= c && c <= '9') {
                sb.appendCodePoint(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }



}
