/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.metacommand;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import javax.swing.AbstractAction;

/**
 * Action that launches online help.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class OnlineHelpAction extends AbstractAction {

    public OnlineHelpAction() {
        super("Online Help");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            URI helpPage = URI.create("https://community.jboss.org/wiki/AGUIForTheCommandLineInterface");
            Desktop.getDesktop().browse(helpPage);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to retrieve help", ioe);
        }
    }
}
