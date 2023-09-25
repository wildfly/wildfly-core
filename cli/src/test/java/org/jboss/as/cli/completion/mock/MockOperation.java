/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.mock;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author Alexey Loubyansky
 */
public class MockOperation {

    private final String name;
    private List<MockOperationProperty> properties = Collections.emptyList();

    public MockOperation(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<MockOperationProperty> getProperties() {
        return properties;
    }

    public void setProperties(List<MockOperationProperty> properties) {
        this.properties = properties;
    }
}
