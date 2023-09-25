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
 * Use this activator to make an option available if some options are already
 * present. Usage of this class allows CLI to automatically generate command
 * help synopsis.
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractRejectOptionActivator implements RejectOptionActivator {

    private final Set<String> options;

    protected AbstractRejectOptionActivator(String... opts) {
        options = new HashSet<>(Arrays.asList(opts));
    }

    protected AbstractRejectOptionActivator(Set<String> opts) {
        options = opts;
    }

    @Override
    public boolean isActivated(ParsedCommand processedCommand) {
        for (String opt : options) {
            if (ARGUMENT_NAME.equals(opt)) {
                if (processedCommand.argument() != null && processedCommand.argument().value() != null) {
                    return false;
                }
            } else {
                ParsedOption processedOption = processedCommand.findLongOptionNoActivatorCheck(opt);
                if (processedOption != null && processedOption.value() != null) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Set<String> getRejected() {
        return Collections.unmodifiableSet(options);
    }
}
