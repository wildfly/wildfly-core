/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
