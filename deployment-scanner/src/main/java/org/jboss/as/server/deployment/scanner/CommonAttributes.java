/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

/**
 * @author Emanuel Muckenhuber
 */
interface CommonAttributes {

    String AUTO_DEPLOY_ZIPPED = "auto-deploy-zipped";
    String AUTO_DEPLOY_EXPLODED = "auto-deploy-exploded";
    String AUTO_DEPLOY_XML = "auto-deploy-xml";
    String DEPLOYMENT_SCANNER = "deployment-scanner";
    String DEPLOYMENT_TIMEOUT = "deployment-timeout";
    String NAME = "name";
    String PATH = "path";
    String RELATIVE_TO = "relative-to";
    String SCANNER = "scanner";
    String SCAN_ENABLED = "scan-enabled";
    String SCAN_INTERVAL = "scan-interval";
    String RUNTIME_FAILURE_CAUSES_ROLLBACK = "runtime-failure-causes-rollback";

}
