/*
 *
 *  * JBoss, Home of Professional Open Source.
 *  * Copyright $tody.year Red Hat, Inc., and individual contributors
 *  * as indicated by the @author tags.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.jboss.as.test.patching;

import com.google.common.base.Joiner;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

import static org.jboss.as.test.patching.PatchingTestUtil.AS_DISTRIBUTION;
import static org.jboss.as.test.patching.PatchingTestUtil.FILE_SEPARATOR;

/**
 * Overrides the superclass just so it can be run in a separate execution without polluting the output from
 * the superclass version of the test.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class GalleonSlimmedOneOffPatchingScenariosTestCase extends BasicOneOffPatchingScenariosTestCase {

    @Override
    public void testOneOffPatchModifyingMultipleMiscFilesDeletingMultipleMiscFiles() throws Exception {

        // The test assumes there is bin/domain.sh file which does not exist in the slimmed server.
        // So to let the test run, add a file
        final String[] testFilePathSegments = new String[]{"bin", "domain.sh"};
        final String testFilePath = AS_DISTRIBUTION + FILE_SEPARATOR + Joiner.on(FILE_SEPARATOR).join(testFilePathSegments);
        PatchingTestUtil.setFileContent(testFilePath, "java -version");

        super.testOneOffPatchModifyingMultipleMiscFilesDeletingMultipleMiscFiles();
    }
}
