package com.sibim.controller.dialogs;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Juntar resguardos opened for real: the person with the most comes first, all of
 *  their resguardos come marked, one can be left out, and only the marked are joined. */
class JuntarResguardosVentanaTest extends ApplicationTest {

    private Scene escena;

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("juntar-prueba"); u.setUsername("prueba"); u.setNombre("Prueba"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
        escena = new Scene(new Pane(), 200, 100);
        stage.setScene(escena);
        stage.show();
    }

    @AfterEach
    void salir() {
        interact(() -> new ArrayList<>(Window.getWindows()).stream()
            .filter(w -> w.getScene().getRoot() instanceof DialogPane).forEach(Window::hide));
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
    }

    private static Resguardo r(String numero, String quien, String area, int dia, String... codigos) {
        Resguardo r = new Resguardo();
        r.setId(numero); r.setNumero(numero); r.setResguardanteNombre(quien); r.setResguardanteArea(area);
        r.setEstado(Resguardo.ESTADO_ACTIVO); r.setCreadoEn(LocalDateTime.of(2026, 10, dia, 10, 0));
        List<ResguardoItem> items = new ArrayList<>();
        for (String c : codigos) {
            ResguardoItem it = new ResguardoItem();
            it.setProductoId("p-" + c); it.setProductoCodigo(c); it.setProductoNombre("Silla " + c);
            items.add(it);
        }
        r.setItems(items);
        return r;
    }

    @Test
    void marcaYJuntaSoloLosElegidos() throws Exception {
        String rm = "Recursos Materiales y Patrimonio";
        List<Resguardo> todos = List.of(
            r("RSG-2026-000301", "L.A.P JUAN EJEMPLO CRUZ", rm, 1, "RMP/11"),
            r("RSG-2026-000302", "L.A.P JUAN EJEMPLO CRUZ", rm, 2, "RMP/12"),
            r("RSG-2026-000303", "L.A.P JUAN EJEMPLO CRUZ", rm, 3, "RMP/13", "RMP/14"),
            r("RSG-2026-000310", "LAURA PRUEBA SOTO", "Contraloría Municipal", 4, "CONT/01"),
            r("RSG-2026-000311", "LAURA PRUEBA SOTO", "Contraloría Municipal", 5, "CONT/02"));
        AtomicReference<List<Resguardo>> juntados = new AtomicReference<>();
        Platform.runLater(() -> JuntarResguardosDialog.show(todos, escena, suyos -> {
            juntados.set(suyos);
            Resguardo n = r("RSG-2026-000400", "L.A.P JUAN EJEMPLO CRUZ", rm, 9, "RMP/12", "RMP/13", "RMP/14");
            return n;
        }, nuevo -> { }));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane panel = (DialogPane) Window.getWindows().stream().filter(w -> w.getScene().getRoot() instanceof DialogPane)
            .findFirst().orElseThrow().getScene().getRoot();

        @SuppressWarnings("unchecked")
        ListView<JuntarResguardosDialog.Grupo> lista = (ListView<JuntarResguardosDialog.Grupo>) panel.lookup(".rsg-lista");
        assertEquals(2, lista.getItems().size());
        assertEquals(3, lista.getItems().get(0).resguardos().size(), "el que tiene más, primero");
        interact(() -> lista.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        List<CheckBox> casillas = panel.lookupAll(".rsg-tarjeta").stream()
            .map(n -> (CheckBox) ((javafx.scene.layout.HBox) n).getChildren().get(0)).toList();
        assertEquals(3, casillas.size());
        assertTrue(casillas.stream().allMatch(CheckBox::isSelected), "vienen marcados");
        Button juntar = (Button) panel.lookupAll(".btn-primary").stream()
            .filter(n -> n instanceof Button b && "Juntar en uno".equals(b.getText())).findFirst().orElseThrow();
        assertFalse(juntar.isDisabled());

        // The oldest one (listed last) stays out.
        CheckBox viejo = casillas.get(2);
        assertEquals("RSG-2026-000301", viejo.getAccessibleText());
        interact(viejo::fire);
        WaitForAsyncUtils.waitForFxEvents();
        Label resumen = (Label) panel.lookupAll(".table-count-label").stream()
            .filter(n -> n instanceof Label l && l.getText() != null && l.getText().startsWith("Se juntan")).findFirst().orElseThrow();
        assertEquals("Se juntan 2 resguardos en uno con 3 bienes", resumen.getText());

        guardarCaptura(panel);
        // Confirmación y después "¿Abrir el PDF…?": Aceptar y luego No.
        Platform.runLater(juntar::fire);
        responder("Juntar en uno", ButtonBar.ButtonData.OK_DONE);
        responder("Resguardo RSG-2026-000400", ButtonBar.ButtonData.CANCEL_CLOSE);
        assertEquals(List.of("RSG-2026-000303", "RSG-2026-000302"),
            juntados.get().stream().map(Resguardo::getNumero).toList(), "solo los marcados");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, lista.getItems().size(), "le quedan el nuevo y el que no se juntó");
    }

    /** Answers the confirmation titled {@code titulo} with the button of {@code cual}. */
    private void responder(String titulo, ButtonBar.ButtonData cual) throws Exception {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w instanceof Stage s && titulo.equals(s.getTitle())));
        WaitForAsyncUtils.waitForFxEvents();
        Stage s = (Stage) Window.getWindows().stream().filter(w -> w instanceof Stage st && titulo.equals(st.getTitle()))
            .findFirst().orElseThrow();
        DialogPane dp = (DialogPane) s.getScene().getRoot();
        ButtonType bt = dp.getButtonTypes().stream().filter(b -> b.getButtonData() == cual).findFirst()
            .orElse(dp.getButtonTypes().get(cual == ButtonBar.ButtonData.OK_DONE ? 0 : dp.getButtonTypes().size() - 1));
        interact(() -> ((Button) dp.lookupButton(bt)).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** For a person to look at: target/capturas/juntar-resguardos.png. */
    private void guardarCaptura(Node n) {
        interact(() -> {
            try {
                java.io.File dir = new java.io.File("target/capturas");
                dir.mkdirs();
                javafx.scene.image.WritableImage img = n.snapshot(null, null);
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                javax.imageio.ImageIO.write(bi, "png", new java.io.File(dir, "juntar-resguardos.png"));
            } catch (Exception ignorada) { /* only a picture */ }
        });
    }
}
