package app.yurune.servlet;

import app.yurune.constant.InfoConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 「覚えたことを見る」の画面の入口。ログイン中の本人の集計だけを見せる。
 */
@WebServlet(InfoConstants.PATH_MEMORY)
public final class MemoryServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getUserComponents(request).getMemoryController().showMemory(request));
    }
}
