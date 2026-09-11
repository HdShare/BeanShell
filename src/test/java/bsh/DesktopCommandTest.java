package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredTestRunner.class)
public class DesktopCommandTest {

    private Interpreter bsh;

    @Before
    public void startDesktopHeadless() throws Exception {
        // desktop() would open a real window unless AWT is headless.
        assumeTrue(GraphicsEnvironment.isHeadless());
        bsh = new Interpreter();
        clearDesktop();
        // Stops at new JFrame, after its listeners are defined and bsh.system.desktop is set.
        bsh.eval("try { desktop(); } catch (java.awt.HeadlessException e) { }");
    }

    @After
    public void clearDesktop() throws Exception {
        if (bsh != null)
            bsh.eval("if (bsh.system.desktop != void) unset(\"bsh.system.desktop\");");
    }

    @Test
    public void taskbar_action_for_unknown_button_is_ignored() throws Exception {
        bsh.eval("bsh.system.desktop.taskBarButtonListener.actionPerformed("
            + "new java.awt.event.ActionEvent(new Object(), 0, \"x\"));");
    }

    @Test
    public void popup_shows_only_for_popup_trigger() throws Exception {
        Object calls = bsh.eval(TestUtil.script(
            "import java.awt.event.MouseEvent;",
            "d = bsh.system.desktop;",
            "calls = new ArrayList();",
            "d.popup = new javax.swing.JPopupMenu() {",
            "    public void show(java.awt.Component c, int x, int y) { calls.add(x + \",\" + y); }",
            "};",
            "d.mousePressed(new MouseEvent(d.pane, MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 2, 1, false));",
            "d.mouseReleased(new MouseEvent(d.pane, MouseEvent.MOUSE_RELEASED, 0L, 0, 3, 4, 1, false));",
            "d.mousePressed(new MouseEvent(d.pane, MouseEvent.MOUSE_PRESSED, 0L, 0, 5, 6, 1, true));",
            "d.mouseReleased(new MouseEvent(d.pane, MouseEvent.MOUSE_RELEASED, 0L, 0, 7, 8, 1, true));",
            "return calls;"
        ));
        assertEquals(Arrays.asList("5,6", "7,8"), calls);
    }
}
