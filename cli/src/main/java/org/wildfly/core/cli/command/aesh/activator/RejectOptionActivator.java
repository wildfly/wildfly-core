/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh.activator;

import java.util.Set;
import org.aesh.command.activator.OptionActivator;

/**
 * An Option Activator that expresses conflicts with other options. Usage of
 * this interface allows CLI to automatically generate command help synopsis.
 *
 * @author jdenise@redhat.com
 */
public interface RejectOptionActivator extends OptionActivator {
    Set<String> getRejected();
}
