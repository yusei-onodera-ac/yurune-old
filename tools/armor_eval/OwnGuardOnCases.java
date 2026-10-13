import app.yurune.service.ForbiddenDomainFilter;
import app.yurune.service.InjectionDetector;
import app.yurune.service.InputGuard;
import app.yurune.service.InputVerdict;
import app.yurune.service.PiiMasker;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * cases.json の文を、アプリ自前の入力の検査（InputGuard）に通して、結果を出す。Model Armor には送らない。
 * 使い方（コマンドプロンプト、プロジェクトのフォルダで。先に mvn package などで target\classes を作る）:
 *   java -cp target\classes;（gson の jar） tools\armor_eval\OwnGuardOnCases.java
 * 出力の 1 行目は、「見つけてほしい文」を、自前の検査が止めた数と、「通してほしい文」を、誤って止めた数。
 */
public final class OwnGuardOnCases {

    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of("tools", "armor_eval", "cases.json"), StandardCharsets.UTF_8);
        InputGuard guard = new InputGuard(new PiiMasker(), new InjectionDetector(), new ForbiddenDomainFilter());

        int shouldMatch = 0;
        int stopped = 0;
        int shouldPass = 0;
        int wronglyStopped = 0;
        StringBuilder detail = new StringBuilder();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("cases")) {
            var item = element.getAsJsonObject();
            String category = item.get("category").getAsString();
            String expect = item.get("expect").getAsString();
            String text = item.get("text").getAsString();
            InputVerdict verdict = guard.inspect(text);
            boolean stoppedHere = verdict.getOutcome() != InputVerdict.Outcome.PASS;
            if ("match".equals(expect)) {
                shouldMatch++;
                stopped += stoppedHere ? 1 : 0;
            } else if ("pass".equals(expect)) {
                shouldPass++;
                wronglyStopped += stoppedHere ? 1 : 0;
            }
            detail.append(String.format("[%s] %s -> %s  %s%n", category, expect, verdict.getOutcome().asId(), text));
        }
        System.out.printf("expect=match: stopped %d/%d   expect=pass: wrongly stopped %d/%d%n%n", stopped, shouldMatch, wronglyStopped, shouldPass);
        System.out.print(detail);
    }
}
