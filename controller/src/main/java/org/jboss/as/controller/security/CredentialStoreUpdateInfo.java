/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.security;

/**
 * Information related to an automatic credential store update.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class CredentialStoreUpdateInfo {

    private String clearText;
    private String previousAlias;
    private String previousClearText;

    CredentialStoreUpdateInfo(String clearText) {
        this.clearText = clearText;
    }

    /**
     * Get the new clear text password.
     *
     * @return the new clear text password
     */
    public String getClearText() {
        return clearText;
    }

    /**
     * Get the previous alias.
     *
     * @return the previous alias or {@code null} if this did not exist before
     */
    public String getPreviousAlias() {
        return previousAlias;
    }

    /**
     * Get the previous clear text password.
     *
     * @return the previous clear text password or {@code null} if this did not exist before
     */
    public String getPreviousClearText() {
        return previousClearText;
    }

    void setPreviousAlias(String previousAlias) {
        this.previousAlias = previousAlias;
    }

    void setPreviousClearText(String previousClearText) {
        this.previousClearText = previousClearText;
    }
}


