/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.runtime;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface called at boot time to tune bootable jar configuration.
 */
public interface BootableServerConfigurator {
    /**
     *
     * @param arguments The Bootable JAR startup arguments.
     * @param installDir The path where the server is installed.
     * @return A configuration instance.
     * @throws Exception
     */
    public Configuration configure(List<String> arguments, Path installDir) throws Exception;
}
