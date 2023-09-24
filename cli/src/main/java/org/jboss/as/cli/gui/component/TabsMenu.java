/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.component;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import org.jboss.as.cli.gui.CliGuiContext;
import org.jboss.as.cli.gui.metacommand.ExploreNodeAction;

/**
 * Extension of JMenu that creates and manages tabs.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class TabsMenu extends JMenu {

    private CliGuiContext cliGuiCtx;

    public TabsMenu(CliGuiContext cliGuiCtx) {
        super("Tabs");
        this.cliGuiCtx = cliGuiCtx;
        setMnemonic(KeyEvent.VK_T);
        addMenuListener(new TabsMenuListener());
    }

    private class TabsMenuListener implements MenuListener {

        public void menuSelected(MenuEvent e) {
            TabsMenu.this.removeAll();
            ExploreNodeAction exploreAction = new ExploreNodeAction(cliGuiCtx);
            JMenuItem exploreSelectedNode = new JMenuItem(exploreAction);
            exploreSelectedNode.setMnemonic(KeyEvent.VK_E);

            if ((exploreAction.getSelectedNode() == null) || exploreAction.getSelectedNode().isLeaf()) {
                exploreSelectedNode.setEnabled(false);
            }

            add(exploreSelectedNode);
            addSeparator();

            JTabbedPane tabs = cliGuiCtx.getTabs();
            for (int i=0; i < tabs.getTabCount(); i++) {
                GoToTabAction action = new GoToTabAction(i, tabs.getTitleAt(i));
                JMenuItem item = new JMenuItem(action);
                item.setToolTipText(tabs.getToolTipTextAt(i));
                add(item);
            }
        }

        public void menuDeselected(MenuEvent e) {
        }

        public void menuCanceled(MenuEvent e) {
        }
    }

    private class GoToTabAction extends AbstractAction {

        private int index;

        public GoToTabAction(int index, String title) {
            super(title);
            this.index = index;
        }
        public void actionPerformed(ActionEvent e) {
            cliGuiCtx.getTabs().setSelectedIndex(index);
        }
    }
}
