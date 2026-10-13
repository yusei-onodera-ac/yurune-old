package app.yurune.controller;

/**
 * Controller が Servlet に返す、次の行き先（画面を表示するか、別の画面へ移すか）。
 */
public final class Navigation {

    private final boolean redirect;
    private final String target;

    private Navigation(boolean redirect, String target) {
        this.redirect = redirect;
        this.target = target;
    }

    public static Navigation forward(String viewPath) {
        return new Navigation(false, viewPath);
    }

    public static Navigation redirect(String path) {
        return new Navigation(true, path);
    }

    public boolean isRedirect() {
        return redirect;
    }

    public String getTarget() {
        return target;
    }
}
