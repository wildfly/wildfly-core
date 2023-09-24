/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh.activator;

/**
 * When an Option is only valid in domain mode, it must be activated by an
 * {@code org.aesh.command.activator.OptionActivator} that implements this
 * interface. Usage of this interface allows CLI to automatically generate
 * command help synopsis.
 *
 * @author jdenise@redhat.com
 */
public interface DomainOptionActivator {

}
