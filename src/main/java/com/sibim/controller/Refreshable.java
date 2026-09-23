package com.sibim.controller;

/** Controllers with background auto-refresh timers implement this so
 *  MainController can stop them on navigation without knowing their type. */
public interface Refreshable {
    void stopAutoRefresh();
}
