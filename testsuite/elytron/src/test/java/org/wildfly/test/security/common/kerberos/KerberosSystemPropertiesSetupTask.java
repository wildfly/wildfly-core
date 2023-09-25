/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.kerberos;

import java.util.HashMap;
import java.util.Map;

import org.wildfly.test.security.common.AbstractSystemPropertiesServerSetupTask;

/**
 * ServerSetup task which configures server system properties for Kerberos testing - path to {@code krb5.conf} file etc.
 *
 * @author Josef Cacek
 */
public class KerberosSystemPropertiesSetupTask extends AbstractSystemPropertiesServerSetupTask {

    /**
     * Returns "java.security.krb5.conf" and "sun.security.krb5.debug" properties.
     *
     * @return Kerberos properties
     * @see org.jboss.as.test.integration.security.common.AbstractSystemPropertiesServerSetupTask#getSystemProperties()
     */
    @Override
    protected Map<String, String> getSystemProperties() {
        final Map<String, String> map = new HashMap<String, String>();
        map.put("java.security.krb5.conf", AbstractKrb5ConfServerSetupTask.getKrb5ConfFullPath());
        map.put("sun.security.krb5.debug", "true");
        return map;
    }

}