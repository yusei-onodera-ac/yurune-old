package app.yurune.sample;

import java.util.List;

/**
 * 審査用サンプルデータ（仕様書 6 節）。画面には「サンプル」と明記する。
 * 並びは、日付、音、会話、音量を下げる分数、朝の答え（2=fit、1=neutral、0=not_fit）。
 * 集計の数字は書かず、学習の計算に通して作る。
 */
public final class SampleNightData {

    public static final List<SampleNight> USER_A = List.of(
            new SampleNight("10/1", "rain", "none", 30, 1),
            new SampleNight("10/2", "rain", "on", 30, 0),
            new SampleNight("10/3", "wave", "none", 30, 2),
            new SampleNight("10/4", "rain", "none", 30, 2),
            new SampleNight("10/5", "rain", "none", 45, 2),
            new SampleNight("10/6", "wave", "none", 45, 1),
            new SampleNight("10/7", "rain", "none", 45, 2));

    public static final List<SampleNight> USER_B = List.of(
            new SampleNight("10/1", "rain", "none", 30, 0),
            new SampleNight("10/2", "rain", "on", 30, 2),
            new SampleNight("10/3", "forest", "on", 30, 2),
            new SampleNight("10/4", "forest", "on", 45, 2),
            new SampleNight("10/5", "rain", "on", 45, 1),
            new SampleNight("10/6", "forest", "none", 30, 0),
            new SampleNight("10/7", "forest", "on", 30, 2));

    private SampleNightData() {
    }
}
