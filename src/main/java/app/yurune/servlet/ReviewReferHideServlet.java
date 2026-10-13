package app.yurune.servlet;

import app.yurune.constant.NoteSummaryConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 「今は表示しない」（受診のすすめを、しばらく出さない）。POST だけを受け付ける（CSRF は、フィルターが確かめる）。
 */
@WebServlet(NoteSummaryConstants.PATH_REFER_HIDE)
public final class ReviewReferHideServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getUserComponents(request).getReviewController().hideReferral());
    }
}
