/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations;

import java.util.List;

import org.jboss.as.controller.OperationContext;

/**
 * @author Stuart Douglas
 */
public class OperationAttachments {


    public static final OperationContext.AttachmentKey<List<DomainOperationTransmuter>> SLAVE_SERVER_OPERATION_TRANSMUTERS = OperationContext.AttachmentKey.create(List.class);


    private OperationAttachments() {

    }

}
