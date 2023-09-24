/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.perdeploy;

import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DeploymentBaseTestCase extends AbstractLoggingTestCase {

    static JavaArchive createDeployment(final String fileName) {
        return createDeployment().addAsResource(DeploymentBaseTestCase.class.getPackage(), fileName, "META-INF/" + fileName);
    }

    static JavaArchive createDeployment(final Class<? extends ServiceActivator> serviceActivator, final String fileName, final Class<?>... classes) {
        final JavaArchive archive = createDeployment(serviceActivator, classes);
        archive.addAsResource(DeploymentBaseTestCase.class.getPackage(), fileName, "META-INF/" + fileName);
        return archive;
    }
}
