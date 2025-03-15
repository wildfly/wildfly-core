/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import org.jboss.as.controller.transform.SubsystemExtensionTransformerRegistration;

public class RemotingTransformerRegistration extends SubsystemExtensionTransformerRegistration {
    public RemotingTransformerRegistration() {
        super(RemotingExtension.SUBSYSTEM_NAME, RemotingSubsystemModel.CURRENT, RemotingSubsystemTransformationDescriptionFactory.INSTANCE);
    }
}
