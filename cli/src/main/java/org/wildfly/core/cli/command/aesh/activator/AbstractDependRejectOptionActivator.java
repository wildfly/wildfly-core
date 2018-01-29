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

import java.util.Set;
import org.aesh.command.impl.internal.ParsedCommand;

/**
 *
 * Use this activator to make an option available if a set of options is already
 * present and another set is not present. Usage of this class allows CLI to
 * automatically generate command help synopsis.
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractDependRejectOptionActivator implements DependOptionActivator, RejectOptionActivator {
    private static class ExpectedOptionsActivatorImpl extends AbstractDependOptionActivator {

        ExpectedOptionsActivatorImpl(boolean lax, Set<String> opts) {
            super(lax, opts);
        }
    }

    private static class NotExpectedOptionsActivatorImpl extends AbstractRejectOptionActivator {

        NotExpectedOptionsActivatorImpl(Set<String> opts) {
            super(opts);
        }

    }
    private final DependOptionActivator expected;
    private final RejectOptionActivator notExpected;

    protected AbstractDependRejectOptionActivator(boolean lax, Set<String> expectedOptions, Set<String> notExpectedOptions) {
        this.expected = new ExpectedOptionsActivatorImpl(lax, expectedOptions);
        this.notExpected = new NotExpectedOptionsActivatorImpl(notExpectedOptions);
    }

    @Override
    public boolean isActivated(ParsedCommand processedCommand) {
        if (!expected.isActivated(processedCommand)) {
            return false;
        }
        return notExpected.isActivated(processedCommand);
    }

    @Override
    public Set<String> getRejected() {
        return notExpected.getRejected();
    }

    @Override
    public Set<String> getDependsOn() {
        return expected.getDependsOn();
    }
}
