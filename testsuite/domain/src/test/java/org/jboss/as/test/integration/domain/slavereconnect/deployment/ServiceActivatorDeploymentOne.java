/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.slavereconnect.deployment;

/**
 * ServiceActivator that installs itself as a service and sets a set of system
 * properties read from a "service-activator-deployment.properties" resource in the deployment.
 * If no resource is available, sets a default property.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ServiceActivatorDeploymentOne extends ServiceActivatorBaseDeployment {
    public ServiceActivatorDeploymentOne() {
        super("one");
    }
}
