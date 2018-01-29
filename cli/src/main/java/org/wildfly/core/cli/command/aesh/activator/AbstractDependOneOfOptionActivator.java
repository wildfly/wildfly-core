/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
