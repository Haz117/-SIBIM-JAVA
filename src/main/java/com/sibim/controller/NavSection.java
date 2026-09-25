package com.sibim.controller;

/** The four groups of the sidebar. {@code defaultExpanded} is the accordion state on a fresh install. */
enum NavSection {
    NAVEGACION("NAVEGACIÓN", true),
    OPERACIONES("OPERACIONES", true),
    CONTROL("CONTROL PATRIMONIAL", false),
    SISTEMA("SISTEMA", false);

    final String title;
    final boolean defaultExpanded;

    NavSection(String title, boolean defaultExpanded) {
        this.title = title;
        this.defaultExpanded = defaultExpanded;
    }
}
