/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.transform;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public interface ExtensionTransformerRegistration {

    /** Return name of subsystem this transformer registration is for.
     * @return non null name of the subsystem
     */
    String getSubsystemName();

    /**
     * Registers subsystem tranformers against the SubsystemTransformerRegistration
     *
     * @param subsystemRegistration contains data about the subsystem registration
     */
    void registerTransformers(SubsystemTransformerRegistration subsystemRegistration);

}
