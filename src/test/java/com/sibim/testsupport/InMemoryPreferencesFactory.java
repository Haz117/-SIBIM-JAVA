package com.sibim.testsupport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** Keeps java.util.prefs in memory for the test JVM.
 *
 *  The app remembers column widths, filters, the Reportes period, sidebar
 *  state… in Preferences, which on Windows is the real user's registry. Tests
 *  that drive controllers used to overwrite those values on the developer's
 *  machine. Surefire selects this factory via the
 *  {@code java.util.prefs.PreferencesFactory} system property (see pom.xml),
 *  so every test run starts from empty preferences and leaves none behind. */
public final class InMemoryPreferencesFactory implements PreferencesFactory {

    private static final Preferences USER_ROOT = new Node(null, "");
    private static final Preferences SYSTEM_ROOT = new Node(null, "");

    @Override public Preferences userRoot()   { return USER_ROOT; }
    @Override public Preferences systemRoot() { return SYSTEM_ROOT; }

    private static final class Node extends AbstractPreferences {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final Map<String, Node> children = new ConcurrentHashMap<>();

        Node(Node parent, String name) { super(parent, name); }

        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key)             { return values.get(key); }
        @Override protected void removeSpi(String key)            { values.remove(key); }
        @Override protected String[] keysSpi()                    { return values.keySet().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi()           { return children.keySet().toArray(String[]::new); }
        @Override protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, n -> new Node(this, n));
        }
        @Override protected void removeNodeSpi() {
            if (parent() instanceof Node p) p.children.remove(name());
        }
        @Override protected void syncSpi()  {}
        @Override protected void flushSpi() {}
    }
}
