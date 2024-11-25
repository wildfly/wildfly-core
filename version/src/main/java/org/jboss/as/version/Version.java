/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.version;

import java.io.InputStream;
import java.util.jar.Manifest;

/**
 * Common location to manager the AS version.
 *
 * @author John Bailey
 * @author Jason T. Greene
 */
public class Version {
    public static final String UNKNOWN_CODENAME = "";
    public static final String AS_VERSION;
    public static final String AS_RELEASE_CODENAME;
    public static final int MANAGEMENT_MAJOR_VERSION = 28;
    public static final int MANAGEMENT_MINOR_VERSION = 0;
    public static final int MANAGEMENT_MICRO_VERSION = 0;

    static {
        InputStream stream = Version.class.getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF");
        Manifest manifest = null;
        try {
            if (stream != null)
                manifest = new Manifest(stream);
        } catch (Exception e) {
            // ignored
        }

        String version = null, code = null;
        if (manifest != null) {
            version = manifest.getMainAttributes().getValue("JBossAS-Release-Version");
            code = manifest.getMainAttributes().getValue("JBossAS-Release-Codename");
        }
        if (version == null) {
            version = "Unknown";
        }
        if (code == null || "N/A".equals(code)) {
            code = UNKNOWN_CODENAME;
        }

        AS_VERSION = version;
        AS_RELEASE_CODENAME = code;
    }


}
