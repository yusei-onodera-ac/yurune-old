package app.yurune.servlet;

import app.yurune.constant.ViewConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * ふりかえり画面の入口。
 */
@WebServlet(ViewConstants.PATH_REVIEW)
public final class ReviewServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        forward(request, response, getUserComponents(request).getReviewController().showReview(request));
    }
}
