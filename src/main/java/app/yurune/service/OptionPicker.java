package app.yurune.service;

import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.SelectedOption;

import java.util.List;

/**
 * 候補の中から、今夜試す選択肢を選ぶ。乱数の種は、呼び出し側が渡す（同じ種は、同じ結果になる）。
 * 本番は、トンプソン抽出（ThompsonOptionSelector）を使う。
 */
@FunctionalInterface
public interface OptionPicker {

    SelectedOption pick(Dimension dimension, List<OptionName> candidates, long seed);
}
