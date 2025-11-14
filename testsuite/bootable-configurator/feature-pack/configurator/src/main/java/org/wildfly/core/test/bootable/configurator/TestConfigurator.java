/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.bootable.configurator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wildfly.core.jar.runtime.BootableServerConfigurator;
import org.wildfly.core.jar.runtime.Configuration;

/**
 *
 * @author jdenise
 */
public class TestConfigurator implements BootableServerConfigurator {

    @Override
    public Configuration configure(List<String> args, Path installDir) throws Exception {
        System.out.println("Booting with the test configurator");
        List<String> arguments = new ArrayList<>();
        arguments.add("-Dorg.wildfly.core.test.bootable.configurator.value=foo");
        String[] lst = {"/system-property=org.wildfly.core.test.bootable.configurator.property:add(value=${org.wildfly.core.test.bootable.configurator.value})"};
        return new Configuration(arguments, Arrays.asList(lst));
    }
}
