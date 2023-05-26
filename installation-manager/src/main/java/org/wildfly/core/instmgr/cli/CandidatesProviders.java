/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.instmgr.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.DefaultCompleter.CandidatesProvider;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

class CandidatesProviders {
    private static Collection<String> getChildrenNames(ModelControllerClient client, ModelNode address, String childType) {
        if (client == null) {
            return Collections.emptyList();
        }

        final ModelNode request = new ModelNode();
        request.get(Util.ADDRESS).set(address);
        request.get(Util.OPERATION).set(Util.READ_CHILDREN_NAMES);
        request.get(Util.CHILD_TYPE).set(childType);

        final ModelNode response;
        try {
            response = client.execute(request);
        } catch (IOException e) {
            return Collections.emptyList();
        }
        final ModelNode result = response.get(Util.RESULT);
        if (!result.isDefined()) {
            return Collections.emptyList();
        }
        final List<ModelNode> list = result.asList();
        final List<String> names = new ArrayList<>(list.size());
        for (ModelNode node : list) {
            names.add(node.asString());
        }
        return names;
    }

    static final CandidatesProvider HOSTS = new CandidatesProvider() {
        @Override
        public Collection<String> getAllCandidates(CommandContext ctx) {
            final ModelControllerClient client = ctx.getModelControllerClient();
            final ModelNode address = new ModelNode().setEmptyList();
            return getChildrenNames(client, address, Util.HOST);
        }
    };
}
