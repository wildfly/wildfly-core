/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Manifest helper methods
 *
 * @author Thomas.Diesler@jboss.com
 * @since 15-Nov-2012
 */
public final class ManifestHelper {

    // Hide ctor
    private ManifestHelper() {
    }

    public static boolean hasMainAttributeValue(Manifest manifest, String attribute) {
        return getMainAttributeValue(manifest, attribute) != null;
    }

    public static String getMainAttributeValue(Manifest manifest, String attribute) {
        String result = null;
        if (manifest != null && attribute != null) {
            Attributes attributes = manifest.getMainAttributes();
            result = attributes.getValue(attribute);
        }
        return result;
    }
}
