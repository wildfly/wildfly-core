/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

/**
 * @author Stuart Douglas
 */
public final class RegisteredDeploymentUnitProcessor implements Comparable<RegisteredDeploymentUnitProcessor> {

    private final int priority;
    private final DeploymentUnitProcessor processor;
    private final String subsystemName;


    public RegisteredDeploymentUnitProcessor(final int priority, final DeploymentUnitProcessor processor, final String subsystemName) {
        this.priority = priority;
        this.processor = processor;
        this.subsystemName = subsystemName;
    }

    @Override
    public int compareTo(final RegisteredDeploymentUnitProcessor o) {
        final int rel = Integer.signum(priority - o.priority);
        return rel == 0 ? processor.getClass().getName().compareTo(o.processor.getClass().getName()) : rel;
    }

    public int getPriority() {
        return priority;
    }

    public DeploymentUnitProcessor getProcessor() {
        return processor;
    }

    public String getSubsystemName() {
        return subsystemName;
    }
}
