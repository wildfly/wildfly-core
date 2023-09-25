/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
