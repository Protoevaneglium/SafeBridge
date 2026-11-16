import com.stange.safebridge.NavigationState;
import java.util.Arrays;

public class NavigationTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) {
        NavigationState n = new NavigationState();
        check(n.route().equals("home") && n.page() == 0, "initial home");
        check(!n.back(0), "home has no parent");
        check(n.go("tools", 1), "open tools");
        check(n.go("plan", 3), "open plan");
        check(!n.go("plan", 2), "repeat tap does not push");
        check(n.routes().length == 3 && n.page() == 2, "repeat keeps reading page");
        check(n.back(2) && n.route().equals("tools") && n.page() == 3, "back restores parent page");
        check(n.back(3) && n.route().equals("home") && n.page() == 1, "back restores home page");
        n.go("peers", 1); n.go("youth", 2); n.updatePage(4);
        String[] routes = n.routes(); int[] pages = n.pages();
        NavigationState rotated = new NavigationState(); rotated.restore(routes, pages);
        check(rotated.route().equals("youth") && rotated.page() == 4, "recreation restores current page");
        check(rotated.back(4) && rotated.route().equals("peers") && rotated.page() == 2, "recreation restores history");
        routes[0] = "changed"; pages[1] = 90;
        check(n.routes()[0].equals("home") && n.pages()[1] == 2, "export arrays are defensive");
        check(n.go("home", 4) && n.routes().length == 1 && n.page() == 0, "home clears history");
        n.go("help", 0); n.go("help", -8);
        check(n.page() == 0, "negative current page clamps");
        check(!n.go(null, 0) && !n.go("", 0) && n.route().equals("help"), "bad route rejected");
        for (int i = 0; i < 90; i++) n.go("r" + i, i);
        check(n.routes().length == 32 && n.routes()[0].equals("home"), "bounded history retains home");
        while (n.back(0)) {}
        check(n.route().equals("home"), "bounded history returns home");
        rotated.restore(new String[]{"home", "help"}, new int[]{0});
        check(rotated.route().equals("home"), "mismatched restore resets");
        rotated.restore(new String[]{"help"}, new int[]{0});
        check(rotated.route().equals("home"), "restore requires root");
        rotated.restore(new String[]{"home", "help"}, new int[]{0, -1});
        check(rotated.route().equals("home"), "negative restore resets");
        rotated.restore(new String[]{"home", "help", "help"}, new int[]{0, 0, 0});
        check(rotated.route().equals("home"), "duplicate restored routes reset");
        rotated.restore(new String[]{"home", "home"}, new int[]{0, 0});
        check(rotated.route().equals("home") && rotated.routes().length == 1, "nested home restore resets");
        rotated.restore(null, null);
        check(rotated.route().equals("home"), "null restore resets");
        System.out.println("PASS " + checks + " navigation history/recreation/repeated-tap/bounds cases");
    }
}
