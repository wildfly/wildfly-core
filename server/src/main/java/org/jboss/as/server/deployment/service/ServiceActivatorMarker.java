/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.service;

import org.jboss.as.server.deployment.AttachmentKey;

/**
 * Marker file used to identify deployments that contain service activators.
 *
 * @author John Bailey
 */
public class ServiceActivatorMarker {
    static final AttachmentKey<ServiceActivatorMarker> ATTACHMENT_KEY = AttachmentKey.create(ServiceActivatorMarker.class);
}
