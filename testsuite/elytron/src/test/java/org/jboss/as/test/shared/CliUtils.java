/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.shared;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

/**
 * CLI helper methods.
 *
 * @author Josef Cacek
 */
public class CliUtils {

    /**
     * Escapes given path String for CLI.
     *
     * @param path path string to escape (must be not-<code>null</code>)
     * @return escaped path
     */
    public static String escapePath(String path) {
        return checkNotNullParamWithNullPointerException("path", path).replace("\\", "\\\\");
    }
}
