/*
Copyright 2015 Red Hat, Inc.

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

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;

import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.logging.ControllerLogger;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * {@link BlockingTimeout} implementation.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
class BlockingTimeoutImpl implements BlockingTimeout {

    private static final int DEFAULT_TIMEOUT = 300;  // seconds
    private static final String DEFAULT_TIMEOUT_STRING = Long.toString(DEFAULT_TIMEOUT);
    private static final int SHORT_TIMEOUT = 5000;
    // Time to add onto the base timeout to allow a response from a remote
    // process to propagate back to us after the timeout occurs on that process
    // This value is arbitrary; meant to be long enough to avoid missing responses
    // delayed by minor things like short gc pauses. The system is meant to work correctly
    // even if the response is in transit when this added timeout expires, so there
    // isn't a great value in making it real high, while there is a cost in added
    // delay before the system unblocks in cases where there is no remote response coming.
    private static final int DEFAULT_DOMAIN_TIMEOUT_ADDER = 5000;
    private static final String DEFAULT_DOMAIN_TIMEOUT_STRING = Long.toString(DEFAULT_DOMAIN_TIMEOUT_ADDER);
    public static final String DOMAIN_TEST_SYSTEM_PROPERTY = "org.wildfly.unsupported.test.domain-timeout-adder";
    private static String sysPropLocalValue;
    private static int defaultLocalValue;
    private static String sysPropDomainValue;
    private static int defaultDomainValue;

    private final int blockingTimeout;
    private final int shortTimeout;
    private final int domainTimeoutAdder;
    private volatile boolean localTimeoutDetected;
    // Guarded by 'this'
    private Set<PathAddress> domainTimeouts;


    BlockingTimeoutImpl(final Integer opHeaderValue) {
        if (opHeaderValue != null) {
            blockingTimeout = opHeaderValue * 1000;
        } else {
            blockingTimeout = resolveDefaultTimeout();
        }
        shortTimeout = Math.min(blockingTimeout, SHORT_TIMEOUT);
        domainTimeoutAdder = resolveDomainTimeoutAdder();
    }

    private static int resolveDefaultTimeout() {
        String propValue = WildFlySecurityManager.getPropertyPrivileged(SYSTEM_PROPERTY, DEFAULT_TIMEOUT_STRING);
        if (sysPropLocalValue == null || !sysPropLocalValue.equals(propValue)) {
            // First call or the system property changed
            sysPropLocalValue = propValue;
            int number = -1;
            try {
                number = Integer.valueOf(sysPropLocalValue);
            } catch (NumberFormatException nfe) {
                // ignored
            }

            if (number > 0) {
                defaultLocalValue = number * 1000; // seconds to ms
            } else {
                ControllerLogger.MGMT_OP_LOGGER.invalidDefaultBlockingTimeout(sysPropLocalValue, SYSTEM_PROPERTY, DEFAULT_TIMEOUT);
                defaultLocalValue = DEFAULT_TIMEOUT * 1000; // seconds to ms
            }
        }
        return defaultLocalValue;
    }

    /** Allows testsuites to shorten the domain timeout adder */
    private static int resolveDomainTimeoutAdder() {
        String propValue = WildFlySecurityManager.getPropertyPrivileged(DOMAIN_TEST_SYSTEM_PROPERTY, DEFAULT_DOMAIN_TIMEOUT_STRING);
        if (sysPropDomainValue == null || !sysPropDomainValue.equals(propValue)) {
            // First call or the system property changed
            sysPropDomainValue = propValue;
            int number = -1;
            try {
                number = Integer.valueOf(sysPropDomainValue);
            } catch (NumberFormatException nfe) {
                // ignored
            }

            if (number > 0) {
                defaultDomainValue = number; // this one is in ms
            } else {
                ControllerLogger.MGMT_OP_LOGGER.invalidDefaultBlockingTimeout(sysPropDomainValue, DOMAIN_TEST_SYSTEM_PROPERTY, DEFAULT_DOMAIN_TIMEOUT_ADDER);
                defaultDomainValue = DEFAULT_DOMAIN_TIMEOUT_ADDER;
            }
        }
        return defaultDomainValue;
    }

    @Override
    public final int getLocalBlockingTimeout() {
        return localTimeoutDetected ? shortTimeout : blockingTimeout;
    }

    @Override
    public final int getProxyBlockingTimeout(PathAddress targetAddress, ProxyController proxyController) {
        final PathAddress processAddress = getProcessAddress(targetAddress);
        // if the proxy address size is less than the process address, that means the timeout
        // is for a server controlled by a different host, so we have double the normal
        // domain timeout adder to account for 2 possible delays
        int multiple = proxyController.getProxyNodeAddress().size() < processAddress.size() ? 2 : 1;
        return getProcessBaseTimeout(processAddress) + (multiple * domainTimeoutAdder);
    }

    @Override
    public int getDomainBlockingTimeout(boolean multipleProxies) {
        // if we are master, we have double the normal
        // domain timeout adder to account for 2 possible delays
        int delayMultiple = multipleProxies ? 2 : 1;
        return blockingTimeout + (delayMultiple * domainTimeoutAdder);
    }

    @Override
    public final void timeoutDetected() {
        localTimeoutDetected = true;
    }

    @Override
    public void proxyTimeoutDetected(PathAddress targetAddress) {
        final PathAddress processAddress = getProcessAddress(targetAddress);
        synchronized (this) {
            if (domainTimeouts == null) {
                domainTimeouts = new HashSet<>();
            }
            domainTimeouts.add(processAddress);
        }
    }

    private int getProcessBaseTimeout(PathAddress processAddress) {
        synchronized (this) {
            return domainTimeouts != null && domainTimeouts.contains(processAddress) ? shortTimeout : blockingTimeout;
        }
    }

    private static PathAddress getProcessAddress(PathAddress targetAddress) {
        if (targetAddress.size() < 2) {
            return targetAddress;
        } else if (RUNNING_SERVER.equals(targetAddress.getElement(1).getKey())) {
            return targetAddress.subAddress(0, 2);
        } else {
            return targetAddress.subAddress(0, 1);
        }

    }
}
