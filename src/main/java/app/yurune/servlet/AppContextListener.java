package app.yurune.servlet;

import app.yurune.AppComponents;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.SecurityConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.domain.value.ControlState;
import app.yurune.domain.value.DegradationKind;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;

/**
 * アプリの起動時に、部品を 1 度だけ組み立てて共有し、セッション Cookie の守りを設定する。
 */
@WebListener
public final class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        DeploymentConfig deployment = DeploymentConfig.fromEnvironment(System::getenv);
        configureSessionCookie(context.getSessionCookieConfig(), deployment);
        AppComponents components = AppComponents.create(deployment);
        context.setAttribute(ViewConstants.ATTR_COMPONENTS, components);
        // 停止スイッチの状態は、起動時に 1 回だけ記録する（AppComponents のコンストラクタに置くと、
        // 多数のテストが AppComponents.create() を繰り返し呼ぶため、記録が溢れる）。
        components.getAuditLogger().control(DegradationKind.AGENT_DISABLED,
                components.getConfig().isAgentEnabled() ? ControlState.ON : ControlState.OFF);
    }

    /** Cookie は、スクリプトから読めず、HTTPS でだけ送られ、別のサイトからの操作には付かない。 */
    public void configureSessionCookie(SessionCookieConfig cookie, DeploymentConfig deployment) {
        cookie.setHttpOnly(true);
        cookie.setSecure(deployment.isCookieSecure());
        cookie.setAttribute(SecurityConstants.COOKIE_SAME_SITE_ATTRIBUTE, SecurityConstants.COOKIE_SAME_SITE_VALUE);
    }
}
