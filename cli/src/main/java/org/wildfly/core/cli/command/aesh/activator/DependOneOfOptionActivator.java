/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh.activator;

import java.util.Set;
import org.aesh.command.activator.OptionActivator;

/**
 * An option activator that expresses that an option depends on a set of options
 * that are in conflict with each others.
 * *
 * @author jdenise@redhat.com
 */
public interface DependOneOfOptionActivator extends OptionActivator {

    Set<String> getOneOfDependsOn();
}
