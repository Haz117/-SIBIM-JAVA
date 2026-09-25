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
}
