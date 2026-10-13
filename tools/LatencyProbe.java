import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 声のやり取りのうち、LLM の応答の遅延を測る（未決事項 U-03）。
 * 夜の会話はゆっくりなので、遅延の影響は小さいと見ている。夜の制御ループのモデルは、この道具で測って決めた
 * （利用者の測定で、全体の p50 は約 0.9 秒、p95 は約 1.4 秒。LlmConstants.DEFAULT_MODEL）。
 *
 * 使い方（Java 21）:
 *   set GEMINI_API_KEY=（AI Studio で作ったキー）
 *   set GEMINI_MODEL=（使うモデルの名前）
 *   java tools/LatencyProbe.java 20
 * 引数は、測る回数（省略時は 10）。
 *
 * 測るもの: 全体の時間と、最初の応答が届くまでの時間（p50 と p95）。
 * 測らないもの: 音声認識（STT）と音声合成（TTS）。これらは Google Cloud の API で、請求先アカウントが必要。
 * 注意: キーはコマンドを実行する端末の環境変数だけに置く。ファイルや Git に書かない。
 * 声のやり取りの全体（音声認識と音声合成を含む）の遅延は、まだ測っていない。
 */
public final class LatencyProbe {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int DEFAULT_RUNS = 10;
    private static final int TIMEOUT_SEC = 30;
    private static final double P50 = 0.50;
    private static final double P95 = 0.95;
    private static final double NANOS_PER_MS = 1_000_000.0;

    /** 個人情報を含まない、短い固定の発話。 */
    private static final String PROMPT =
            "あなたは、眠る前の人に寄り添う「ぽてね」です。診断や助言はしません。"
            + "「ちょっと考えごとしてた」と言われたときの、やさしい一言を、30文字以内で返してください。";

    public static void main(String[] args) throws Exception {
        String key = System.getenv("GEMINI_API_KEY");
        String model = System.getenv("GEMINI_MODEL");
        if (key == null || key.isBlank() || model == null || model.isBlank()) {
            System.err.println("GEMINI_API_KEY と GEMINI_MODEL を、環境変数に設定してください。");
            System.exit(1);
        }
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_RUNS;

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SEC)).build();
        List<Double> totals = new ArrayList<>();
        List<Double> firsts = new ArrayList<>();
        for (int run = 0; run < runs; run++) {
            totals.add(measureTotal(client, key, model));
            firsts.add(measureFirstChunk(client, key, model));
        }
        report("全体（生成が終わるまで）", totals);
        report("最初の応答（ストリーミング）", firsts);
    }

    private static double measureTotal(HttpClient client, String key, String model) throws Exception {
        HttpRequest request = buildRequest(key, ENDPOINT + model + ":generateContent");
        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requireOk(response.statusCode());
        return (System.nanoTime() - start) / NANOS_PER_MS;
    }

    private static double measureFirstChunk(HttpClient client, String key, String model) throws Exception {
        HttpRequest request = buildRequest(key, ENDPOINT + model + ":streamGenerateContent?alt=sse");
        long start = System.nanoTime();
        HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        requireOk(response.statusCode());
        try (java.io.InputStream body = response.body()) {
            body.read();
            return (System.nanoTime() - start) / NANOS_PER_MS;
        }
    }

    private static HttpRequest buildRequest(String key, String url) {
        String json = "{\"contents\":[{\"parts\":[{\"text\":\"" + escape(PROMPT) + "\"}]}]}";
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private static void requireOk(int status) {
        if (status != 200) {
            throw new IllegalStateException("HTTP " + status + "（キー、モデル名、利用上限を確認してください）");
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void report(String label, List<Double> samples) {
        List<Double> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        System.out.printf("%s: 回数=%d  p50=%.0fms  p95=%.0fms  最大=%.0fms%n", label, sorted.size(),
                percentile(sorted, P50), percentile(sorted, P95), sorted.get(sorted.size() - 1));
    }

    private static double percentile(List<Double> sorted, double ratio) {
        int index = (int) Math.ceil(ratio * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
