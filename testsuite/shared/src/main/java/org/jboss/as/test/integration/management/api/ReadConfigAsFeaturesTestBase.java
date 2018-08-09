/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.api;

import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

import java.util.concurrent.TimeoutException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SPEC;

/**
 * @author <a href="mailto:rjanik@redhat.com">Richard Janík</a>
 */
public abstract class ReadConfigAsFeaturesTestBase {

    @Before
    public void setUp() throws UnsuccessfulOperationException {
        saveDefaultConfig();
        saveDefaultResult();
    }

    @After
    public void tearDown() throws TimeoutException, InterruptedException {
        restoreDefaultConfig();
    }

    protected abstract void saveDefaultConfig() throws UnsuccessfulOperationException;

    protected abstract void saveDefaultResult() throws UnsuccessfulOperationException;

    protected abstract void restoreDefaultConfig() throws TimeoutException, InterruptedException;

    protected ModelNode getFeatureNodeChild(ModelNode node, String spec) {
        return getListElement(node.get(CHILDREN), spec);
    }

    protected ModelNode getFeatureNodeChild(ModelNode node, String spec, ModelNode id) {
        return getListElement(node.get(CHILDREN), spec, id);
    }

    protected int getFeatureNodeChildIndex(ModelNode node, String spec) {
        return getListElementIndex(node.get(CHILDREN), spec);
    }

    protected int getFeatureNodeChildIndex(ModelNode node, String spec, ModelNode id) {
        return getListElementIndex(node.get(CHILDREN), spec, id);
    }

    protected ModelNode getListElement(ModelNode list, String spec) {
        return getListElement(list, spec, null);
    }

    protected ModelNode getListElement(ModelNode list, String spec, ModelNode id) {
        for (ModelNode element : list.asList()) {
            if (element.get(SPEC).asString().equals(spec) &&
                    (id == null || id.equals(element.get(ID)))) {
                return element;
            }
        }

        throw new IllegalArgumentException("no element for spec " + spec + " and id " + ((id == null) ? "null" : id.toJSONString(true)));
    }

    protected int getListElementIndex(ModelNode list, String spec) {
        return getListElementIndex(list, spec, null);
    }

    protected int getListElementIndex(ModelNode list, String spec, ModelNode id) {
        for (int i = 0; i < list.asList().size(); i++) {
            if (list.get(i).get(SPEC).asString().equals(spec) &&
                    (id == null || id.equals(list.get(i).get(ID)))) {
                return i;
            }
        }
        throw new IllegalArgumentException("no element for spec " + spec + " and id " + ((id == null) ? "null" : id.toJSONString(true)));
    }
}
