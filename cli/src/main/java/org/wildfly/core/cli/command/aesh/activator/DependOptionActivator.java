/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh.activator;

import java.util.Set;
import org.aesh.command.activator.OptionActivator;

/**
 * An Option Activator that expresses dependencies on other options. Usage of
 * this interface allows CLI to automatically generate command help synopsis.
 *
 * @author jdenise@redhat.com
 */
public interface DependOptionActivator extends OptionActivator {

    // XXX jfdenise.
    /**
     * The name of a Command Option annotated with
     * {@code @Argument or @Arguments}
     */
    String ARGUMENT_NAME = "";
    Set<String> getDependsOn();
}
