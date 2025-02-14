package com.ctgraphdep.model;

import javax.swing.*;

public class DialogComponents {
    public final JDialog dialog;
    public final JPanel buttonsPanel;

    public DialogComponents(JDialog dialog, JPanel buttonsPanel) {
        this.dialog = dialog;
        this.buttonsPanel = buttonsPanel;
    }
}