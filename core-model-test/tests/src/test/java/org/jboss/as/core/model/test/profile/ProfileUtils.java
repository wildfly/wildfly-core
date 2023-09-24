/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.profile;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIBE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProfileUtils {

    public static void executeDescribeProfile(KernelServices kernelServices, String profileName) throws OperationFailedException {
        ModelNode op = new ModelNode();
        op.get(OP).set(DESCRIBE);
        op.get(OP_ADDR).add(PROFILE, profileName);
        ModelNode result = kernelServices.executeForResult(op);
        Assert.assertTrue(result.getType() == ModelType.LIST);
        Assert.assertEquals(0, result.asList().size());
    }
}
