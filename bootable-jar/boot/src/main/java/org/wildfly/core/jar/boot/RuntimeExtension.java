/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.boot;

import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author jdenise
 */
public interface RuntimeExtension {
    public void boot(List<String> arguments, Path installDir) throws Exception;
}
