package app.yurune.controller;

import app.yurune.constant.InfoConstants;
import app.yurune.controller.view.SoundCreditView;
import app.yurune.domain.value.SoundCredit;
import app.yurune.service.SoundCreditService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 「音源のクレジット」と「ご利用にあたって」の入出力を受け持つ。どちらも、利用者のデータは使わない。
 */
public final class InfoController {

    private final SoundCreditService soundCreditService;

    public InfoController(SoundCreditService soundCreditService) {
        this.soundCreditService = soundCreditService;
    }

    public Navigation showCredits(HttpServletRequest request) {
        List<SoundCreditView> views = new ArrayList<>();
        for (SoundCredit credit : soundCreditService.findCredits()) {
            views.add(new SoundCreditView(credit));
        }
        request.setAttribute(InfoConstants.ATTR_SOUND_CREDITS, views);
        return Navigation.forward(InfoConstants.VIEW_CREDITS);
    }

    public Navigation showGuide(HttpServletRequest request) {
        return Navigation.forward(InfoConstants.VIEW_GUIDE);
    }
}
