/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.operation;

/**
 * This interface, implemented by legacy and operator container allows to convey
 * the part of the command cleaned from operators and other commands. It is
 * called prior to execute the command and parsed by the legacy CommandContext
 * parser.
 *
 * @author jdenise@redhat.com
 */
public interface SpecialCommand {
    String getLine();
}
