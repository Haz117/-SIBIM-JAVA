package com.sibim.controller;

/** The groups of the sidebar, top to bottom. {@code defaultExpanded} is the accordion state on a fresh install. */
enum NavSection {
    NAVEGACION("INVENTARIO", true),
    OPERACIONES("OPERACIÓN", true),
    REPORTES("REPORTES", true),
    CONTROL("CONTROL PATRIMONIAL", false),
    SISTEMA("SISTEMA", false);

    final String title;
    final boolean defaultExpanded;

    NavSection(String title, boolean defaultExpanded) {
        this.title = title;
        this.defaultExpanded = defaultExpanded;
    }
}
