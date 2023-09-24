/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.dmr.ModelNode;

/**
 * Class that is used to transform an operation before it is send to other servers in the domain.
 *
 * The main use case for this is replacing content addition parameters with a hash into the content
 * repository.
 *
 *
 * @author Stuart Douglas
 * @see OperationAttachments#SLAVE_SERVER_OPERATION_TRANSMUTERS
 * @see  CompositeOperationAwareTransformer
 *
 */
public interface DomainOperationTransmuter {

    ModelNode transmmute(final OperationContext context, final ModelNode operation);

}
