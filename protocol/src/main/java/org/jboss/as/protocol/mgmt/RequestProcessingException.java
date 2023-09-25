/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.protocol.mgmt;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class RequestProcessingException extends Exception {

    public RequestProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestProcessingException(String message) {
        super(message);
    }

    public RequestProcessingException(Throwable cause) {
        super(cause);
    }

}
