package com.sibim.util;

import java.nio.file.Path;

/** A second SIBIM, in its own process (the lock is per process): prints what it found. */
public final class OtraInstancia {
    public static void main(String[] args) {
        System.out.println("RESULTADO=" + InstanciaUnica.adquirir(Path.of(args[0]), null));
        System.exit(0);
    }
}
