/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli;

/**
 * A representation of the SSL Configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface SSLConfig {

    /**
     * @return The location of the keyStore or null if not specified.
     */
    String getKeyStore();

    /**
     * @return The keyStorePassword or null if not specified.
     */
    String getKeyStorePassword();

    /**
     * @return The alias or null if not specified.
     */
    String getAlias();

    /**
     * @return The keyPassword or null if not specified.
     */
    String getKeyPassword();

    /**
     * @return The location of the trustStore or null if not specified.
     */
    String getTrustStore();

    /**
     * @return The trustStorePassword or null if not specified.
     */
    String getTrustStorePassword();

    /**
     * @return true if the CLI should automatically update the trust store.
     */
    boolean isModifyTrustStore();

}
