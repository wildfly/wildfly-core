/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.core.management.client;

import java.util.Map;
import org.wildfly.extension.core.management.client.Process.RunningMode;
import org.wildfly.extension.core.management.client.Process.Type;

/**
 * Initialization parameters for a ProcessStateListener.
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public interface ProcessStateListenerInitParameters {

    Map<String, String> getInitProperties();

    RunningMode getRunningMode();

    Type getProcessType();

    public static class ProcessStateListenerInitParametersImpl implements ProcessStateListenerInitParameters {

        private final Map<String, String> initProperties;
        private final Type processType;
        private final RunningMode runningMode;

        private ProcessStateListenerInitParametersImpl(Builder builder) {
            this.initProperties = builder.initProperties;
            this.processType = builder.processType;
            this.runningMode = builder.runningMode;
        }

        @Override
        public Map<String, String> getInitProperties() {
            return initProperties;
        }

        @Override
        public Type getProcessType() {
            return processType;
        }

        @Override
        public RunningMode getRunningMode() {
            return runningMode;
        }

    }

    public class Builder {

        private Map<String, String> initProperties;
        private Process.Type processType;
        private Process.RunningMode runningMode;

        public Builder() {
        }

        public Builder setInitProperties(Map<String, String> initProperties) {
            this.initProperties = initProperties;
            return this;
        }

        public Builder setProcessType(Process.Type processType) {
            this.processType = processType;
            return this;
        }

        public Builder setRunningMode(Process.RunningMode runningMode) {
            this.runningMode = runningMode;
            return this;
        }

        public ProcessStateListenerInitParameters build() {
            return new ProcessStateListenerInitParameters.ProcessStateListenerInitParametersImpl(this);
        }

    }
}
