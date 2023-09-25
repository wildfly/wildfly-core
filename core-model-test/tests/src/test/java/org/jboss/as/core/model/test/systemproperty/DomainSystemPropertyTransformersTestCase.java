/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.systemproperty;

import org.jboss.as.core.model.test.TransformersTestParameterized;
import org.jboss.as.core.model.test.util.TransformersTestParameter;
import org.junit.runner.RunWith;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(TransformersTestParameterized.class)
public class DomainSystemPropertyTransformersTestCase extends AbstractSystemPropertyTransformersTest {

    public DomainSystemPropertyTransformersTestCase(TransformersTestParameter params) {
        super(params, false);
    }

}
