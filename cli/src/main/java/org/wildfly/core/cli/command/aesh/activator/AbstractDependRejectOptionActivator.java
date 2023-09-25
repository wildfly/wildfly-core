/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
