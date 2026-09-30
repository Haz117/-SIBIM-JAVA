package com.sibim.ui;

import javafx.css.CssParser;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cheap guards for the design system — no display needed. They exist because both
 * failures below are SILENT: the app still opens, the style just never applies.
 *
 * 1. FXML lists (styleClass) are split by JavaFX on commas only. styleClass="a b"
 *    becomes ONE class named "a b" that matches no CSS rule, so `.a` and `.b` never
 *    apply. Write styleClass="a, b".
 * 2. styles.css must parse with zero errors (an unknown property or a broken rule
 *    is otherwise just dropped with a console warning).
 */
class DesignConsistencyTest {

    private static final Pattern STYLE_CLASS = Pattern.compile("styleClass=\"([^\"]*)\"");

    @Test
    void fxml_styleClass_separaClasesConComas() throws Exception {
        Path dir = Path.of(getClass().getResource("/fxml").toURI());
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".fxml")).sorted().toList()) {
                String xml = Files.readString(f);
                Matcher m = STYLE_CLASS.matcher(xml);
                while (m.find()) {
                    for (String part : m.group(1).split(",")) {
                        if (part.strip().matches(".*\\s.*")) {
                            int line = (int) xml.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
                            offenders.add(f.getFileName() + ":" + line + "  styleClass=\"" + m.group(1)
                                + "\"  ->  usa \"" + String.join(", ", m.group(1).strip().split("[,\\s]+")) + "\"");
                            break;
                        }
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
            "JavaFX trata styleClass=\"a b\" como UNA sola clase que ningún CSS reconoce; separa con comas:\n  "
                + String.join("\n  ", offenders));
    }

    @Test
    void stylesCss_parseaSinErrores() throws Exception {
        URL css = getClass().getResource("/css/styles.css");
        assertNotNull(css, "styles.css debe estar en el classpath");
        var errors = CssParser.errorsProperty();   // must be requested before parsing to collect errors
        errors.clear();

        var sheet = new CssParser().parse(css);

        assertFalse(sheet.getRules().isEmpty(), "El CSS no debe quedar vacío");
        List<String> msgs = errors.stream().map(e -> e.getMessage()).limit(20).toList();
        assertEquals(0, errors.size(), "Errores de parseo en styles.css:\n  " + String.join("\n  ", msgs));
    }

    /** An icon name that doesn't exist in the Ikonli pack throws while the FXML
     *  loads, so the whole screen never opens (a bad literal in productos.fxml
     *  once left Bienes blank). Checks every iconLiteral in FXML and every
     *  FontIcon("…") / setIconLiteral("…") in the Java sources. */
    @Test
    void todosLosIconos_existenEnLaLibreria() throws Exception {
        Pattern fxmlIcon = Pattern.compile("iconLiteral=\"([^\"]+)\"");
        Pattern javaIcon = Pattern.compile("(?:new FontIcon|setIconLiteral)\\(\"([a-z0-9]+-[a-z0-9-]+)\"\\)");
        List<String> malos = new ArrayList<>();
        Path fxmlDir = Path.of(getClass().getResource("/fxml").toURI());
        Path javaDir = Path.of("src/main/java");
        if (!Files.isDirectory(javaDir)) javaDir = Path.of("../src/main/java");   // surefire runs from target/
        for (Path[] set : new Path[][]{{fxmlDir}, {javaDir}}) {
            try (Stream<Path> files = Files.walk(set[0])) {
                for (Path f : files.filter(x -> x.toString().endsWith(".fxml") || x.toString().endsWith(".java")).toList()) {
                    Matcher m = (f.toString().endsWith(".fxml") ? fxmlIcon : javaIcon).matcher(Files.readString(f));
                    while (m.find()) {
                        String icon = m.group(1);
                        try {
                            org.kordamp.ikonli.javafx.IkonResolver.getInstance().resolve(icon).resolve(icon);
                        } catch (Exception e) {
                            malos.add(f.getFileName() + ": " + icon);
                        }
                    }
                }
            }
        }
        assertTrue(malos.isEmpty(), "Iconos que no existen en Ikonli:\n  " + String.join("\n  ", malos));
    }

    /** styles.css is only an ordered list of @import of css/partes/. A part that is
     *  not imported (or imported twice) would silently drop (or reorder) its rules,
     *  and each part must parse on its own. */
    @Test
    void stylesCss_importaCadaParteUnaVez_yTraeTodasSusReglas() throws Exception {
        URL index = getClass().getResource("/css/styles.css");
        String indexText = Files.readString(Path.of(index.toURI()));
        Path dir = Path.of(getClass().getResource("/css/partes").toURI());
        List<Path> partes;
        try (Stream<Path> files = Files.list(dir)) {
            partes = files.filter(p -> p.toString().endsWith(".css")).sorted().toList();
        }
        assertFalse(partes.isEmpty());

        var errors = CssParser.errorsProperty();
        int reglas = 0;
        for (Path parte : partes) {
            String imp = "@import url(\"partes/" + parte.getFileName() + "\");";
            int veces = indexText.split(Pattern.quote(imp), -1).length - 1;
            assertEquals(1, veces, parte.getFileName() + " debe importarse exactamente una vez en styles.css");
            errors.clear();
            reglas += new CssParser().parse(parte.toUri().toURL()).getRules().size();
            assertEquals(0, errors.size(), "Errores de parseo en " + parte.getFileName() + ": "
                + errors.stream().map(e -> e.getMessage()).limit(5).toList());
        }
        assertEquals(reglas, new CssParser().parse(index).getRules().size(),
            "styles.css debe traer las reglas de todas las partes (¿@import sin resolver?)");
    }
}
