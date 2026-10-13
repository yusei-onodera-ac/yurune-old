package app.yurune.service;

/**
 * Model Armor を使わない設定のときの窓口。何も検査せず、自前の層だけで動く。
 */
public final class DisabledArmorGateway implements ArmorGateway {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public ArmorVerdict screenPrompt(String text) {
        return ArmorVerdict.safe();
    }

    @Override
    public ArmorVerdict screenResponse(String text) {
        return ArmorVerdict.safe();
    }
}
