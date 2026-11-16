package com.stange.safebridge;

import java.util.ArrayList;

/** Navigation coordinates only. No answers, planning details or calculator input. */
public final class NavigationState {
    private static final int LIMIT = 32;
    private final ArrayList<String> routes = new ArrayList<>();
    private final ArrayList<Integer> pages = new ArrayList<>();

    public NavigationState() { reset(); }
    public String route() { return routes.get(routes.size() - 1); }
    public int page() { return pages.get(pages.size() - 1); }
    public void reset() {
        routes.clear(); pages.clear();
        routes.add("home"); pages.add(0);
    }
    public void updatePage(int page) { pages.set(pages.size() - 1, Math.max(0, page)); }
    private static boolean valid(String route) {
        return route != null && route.matches("[a-z][a-z0-9_-]{0,31}");
    }
    /** Returns whether the route changed; repeated current-route taps do not push. */
    public boolean go(String route, int currentPage) {
        if (!valid(route)) return false;
        updatePage(currentPage);
        if ("home".equals(route)) {
            boolean changed = routes.size() > 1 || page() != 0;
            reset();
            return changed;
        }
        if (route.equals(route())) return false;
        if (routes.size() == LIMIT) { routes.remove(1); pages.remove(1); }
        routes.add(route); pages.add(0);
        return true;
    }
    /** Pops a section after the caller has tried moving to its previous page. */
    public boolean back(int currentPage) {
        updatePage(currentPage);
        if (routes.size() == 1) return false;
        routes.remove(routes.size() - 1); pages.remove(pages.size() - 1);
        return true;
    }
    public String[] routes() { return routes.toArray(new String[0]); }
    public int[] pages() {
        int[] result = new int[pages.size()];
        for (int i = 0; i < result.length; i++) result[i] = pages.get(i);
        return result;
    }
    /** Restores trusted app navigation Bundle values, failing closed to Home. */
    public void restore(String[] restoredRoutes, int[] restoredPages) {
        reset();
        if (restoredRoutes == null || restoredPages == null ||
                restoredRoutes.length != restoredPages.length ||
                restoredRoutes.length == 0 || restoredRoutes.length > LIMIT ||
                !"home".equals(restoredRoutes[0])) return;
        for (int i = 0; i < restoredRoutes.length; i++) {
            if (!valid(restoredRoutes[i]) || restoredPages[i] < 0 ||
                    (i > 0 && ("home".equals(restoredRoutes[i]) ||
                    restoredRoutes[i].equals(restoredRoutes[i - 1])))) return;
        }
        routes.clear(); pages.clear();
        for (int i = 0; i < restoredRoutes.length; i++) {
            routes.add(restoredRoutes[i]); pages.add(restoredPages[i]);
        }
    }
}
