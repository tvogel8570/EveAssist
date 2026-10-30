package com.eveassist.spike.vaadin;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.theme.lumo.Lumo;

/** Page-wide settings: Vaadin's Lumo theme in its dark variant. */
@StyleSheet(Lumo.STYLESHEET)
@ColorScheme(ColorScheme.Value.DARK)
public class AppShell implements AppShellConfigurator {
}
