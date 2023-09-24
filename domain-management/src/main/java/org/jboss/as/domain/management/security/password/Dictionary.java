/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

/**
 * Dictionary interface. Implementing class may provide any means to detect sequence of dictionary entry.
 *
 * @author baranowb
 *
 */
public interface Dictionary {

    /**
     * Detects how long is sequence of chars in password. The sequence MUST be dictionary declared word.
     *
     * @param password
     * @return
     */
    int dictionarySequence(String password);
}
