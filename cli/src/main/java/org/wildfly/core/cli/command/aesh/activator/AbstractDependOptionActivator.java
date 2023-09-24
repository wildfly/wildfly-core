/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh.activator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.impl.internal.ParsedOption;

/**
 *
 * Use this activator to make an option available if some options are already
 * present.Usage of this class allows CLI to automatically generate command help
 * synopsis.
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractDependOptionActivator implements DependOptionActivator {

    private final Set<String> options;
    private final boolean lax;

    protected AbstractDependOptionActivator(boolean lax, String... opts) {
        options = new HashSet<>(Arrays.asList(opts));
        this.lax = lax;
    }

    protected AbstractDependOptionActivator(boolean lax, Set<String> opts) {
        this.lax = lax;
        options = opts;
    }

    @Override
    public boolean isActivated(ParsedCommand processedCommand) {
        boolean found = true;
        for (String opt : options) {
            if (ARGUMENT_NAME.equals(opt)) {
                found &= processedCommand.argument() != null && (lax || processedCommand.argument().value() != null);
            } else {
                ParsedOption processedOption = processedCommand.findLongOptionNoActivatorCheck(opt);
                found &= processedOption != null && (lax || processedOption.value() != null);
            }
        }
        return found;
    }

    @Override
    public Set<String> getDependsOn() {
        return Collections.unmodifiableSet(options);
    }
}
