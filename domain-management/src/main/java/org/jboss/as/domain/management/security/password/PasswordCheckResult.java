/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

/**
 * @author baranowb
 *
 */
public class PasswordCheckResult {

    private final Result result;
    private final String message;

    /**
     * @param result
     * @param message
     */
    PasswordCheckResult(Result result, String message) {
        super();
        this.result = result;
        this.message = message;
    }

    /**
     * @return the result
     */
    public Result getResult() {
        return result;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    public enum Result {
        REJECT, WARN, ACCEPT
    }
}
