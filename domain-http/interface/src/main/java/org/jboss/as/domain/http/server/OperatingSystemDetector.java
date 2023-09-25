/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server;

import java.util.Locale;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OperatingSystemDetector {
    public static final OperatingSystemDetector INSTANCE;

    static {
        final String os = System.getProperty("os.name");
        final boolean windows;
        if (os != null && os.toLowerCase(Locale.ENGLISH).contains("win")) {
            windows = true;
        } else {
            windows = false;
        }
        INSTANCE = new OperatingSystemDetector(windows);
    }

    private final boolean windows;

    private OperatingSystemDetector(boolean windows) {
        this.windows = windows;
    }

    public boolean isWindows() {
        return windows;
    }

}
