/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh;

import org.aesh.command.validator.ValidatorInvocation;
import org.jboss.as.cli.CommandContext;

/**
 * CLI specific {@code ValidatorInvocation} that exposes
 * {@link org.jboss.as.cli.CommandContext}.
 *
 * @author jdenise@redhat.com
 */
public interface CLIValidatorInvocation<T, C> extends ValidatorInvocation<T, C> {

    CommandContext getCommandContext();

}
