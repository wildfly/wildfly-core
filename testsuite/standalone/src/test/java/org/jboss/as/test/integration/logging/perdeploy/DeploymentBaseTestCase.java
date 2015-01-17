/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
