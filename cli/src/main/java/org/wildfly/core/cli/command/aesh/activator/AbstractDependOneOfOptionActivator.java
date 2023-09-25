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
import static org.wildfly.core.cli.command.aesh.activator.DependOptionActivator.ARGUMENT_NAME;

/**
 *
 * Use this activator to make an option to depend on a set of options that are
 * in conflict with each others. Usage of this class allows CLI to automatically
 * generate command help * synopsis.
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractDependOneOfOptionActivator implements DependOneOfOptionActivator {

    private final Set<String> options;

    protected AbstractDependOneOfOptionActivator(String... opts) {
        options = new HashSet<>(Arrays.asList(opts));
    }

    protected AbstractDependOneOfOptionActivator(Set<String> opts) {
        options = opts;
    }

    @Override
    public boolean isActivated(ParsedCommand processedCommand) {
        boolean found = false;
        for (String opt : options) {
            if (ARGUMENT_NAME.equals(opt)) {
                found |= processedCommand.argument() != null && (processedCommand.argument().value() != null);
            } else {
                ParsedOption processedOption = processedCommand.findLongOptionNoActivatorCheck(opt);
                found |= processedOption != null && (processedOption.value() != null);
            }
        }
        return found;
    }

    @Override
    public Set<String> getOneOfDependsOn() {
        return Collections.unmodifiableSet(options);
    }
}
