package app.yurune.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Filter のテスト用の、簡単な擬似の request / response / session。
 * 使うメソッドだけを答え、それ以外は null / 0 / false を返す。
 */
final class FakeHttp {

    private FakeHttp() {
    }

    static final class Exchange {
        final Map<String, String> requestHeaders = new HashMap<>();
        final Map<String, String> parameters = new HashMap<>();
        final Map<String, Object> requestAttributes = new HashMap<>();
        final Map<String, Object> sessionAttributes = new HashMap<>();
        final Map<String, String> responseHeaders = new HashMap<>();
        String method = "GET";
        String servletPath = "/home";
        boolean secure;
        int errorStatus;
        /** getStatus() が返す値。既定は 0（これまでと同じ）。A5 の確認（5xx はログに出す）で、明示的に設定する。 */
        int status;
        String redirectedTo;
        final AtomicInteger chainCalls = new AtomicInteger();

        HttpServletRequest request() {
            HttpSession session = proxy(HttpSession.class, (self, called, args) -> switch (called.getName()) {
                case "getAttribute" -> sessionAttributes.get((String) args[0]);
                case "setAttribute" -> {
                    sessionAttributes.put((String) args[0], args[1]);
                    yield null;
                }
                default -> defaultValue(called.getReturnType());
            });
            return proxy(HttpServletRequest.class, (self, called, args) -> switch (called.getName()) {
                case "getMethod" -> method;
                case "getContextPath" -> "";
                case "getServletPath" -> servletPath;
                case "getHeader" -> requestHeaders.get((String) args[0]);
                case "getParameter" -> parameters.get((String) args[0]);
                case "isSecure" -> secure;
                case "getSession" -> args != null && args.length == 1 && Boolean.FALSE.equals(args[0]) && sessionAttributes.isEmpty() ? null : session;
                case "setAttribute" -> {
                    requestAttributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "getAttribute" -> requestAttributes.get((String) args[0]);
                default -> defaultValue(called.getReturnType());
            });
        }

        HttpServletResponse response() {
            return proxy(HttpServletResponse.class, (self, called, args) -> switch (called.getName()) {
                case "setHeader" -> {
                    responseHeaders.put((String) args[0], (String) args[1]);
                    yield null;
                }
                case "sendRedirect" -> {
                    redirectedTo = (String) args[0];
                    yield null;
                }
                case "sendError" -> {
                    errorStatus = (Integer) args[0];
                    yield null;
                }
                case "getStatus" -> status;
                default -> defaultValue(called.getReturnType());
            });
        }

        FilterChain chain() {
            return (request, response) -> chainCalls.incrementAndGet();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
