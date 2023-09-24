/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.rbac;

/**
* Expected outcome of a management request.
*
* @author Brian Stansberry (c) 2013 Red Hat Inc.
*/
public enum Outcome {
    /** Request was successful */
    SUCCESS,
    /** Request failed with a failure description indicating it authorization failed */
    UNAUTHORIZED,
    /** Request failed with a failure description indicating the target resource was not found */
    HIDDEN,
    /** Request failed with some other failure description */
    FAILED
}
