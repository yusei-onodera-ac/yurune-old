package app.yurune.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 依存とコンテナ、デプロイ用のスクリプトの検証（受け入れ条件ではなく、設計書の「サプライチェーンの検証」と「最小権限」の自動確認）。
 * 依存は固定して数を少なく保つ。ベースイメージは固定する。実行は非 root。権限は広げない。鍵をリポジトリに置かない。
 * ビルド用のコンテナ（ソースだけを持つ）では、対象のファイルがないので、飛ばす。
 */
class SupplyChainPolicyTest {

    /** 使ってよい依存（groupId:artifactId）。増やすときは、ここを変え、理由を説明できるようにする。 */
    private static final Set<String> ALLOWED_DEPENDENCIES = Set.of(
            "jakarta.servlet:jakarta.servlet-api",
            "jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api",
            "org.glassfish.web:jakarta.servlet.jsp.jstl",
            "com.google.code.gson:gson",
            "org.junit.jupiter:junit-jupiter");

    private static String read(String file) throws IOException {
        Path path = Path.of(file);
        Assumptions.assumeTrue(Files.exists(path), file + " がないので飛ばす");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    // ---- 依存 ----

    @Test
    void everyDependencyAndPluginHasAFixedVersion() throws IOException {
        String pom = read("pom.xml");

        for (Matcher matcher = Pattern.compile("<version>([^<]*)</version>").matcher(pom); matcher.find();) {
            String version = matcher.group(1).trim();
            assertFalse(version.matches("(?i).*(LATEST|RELEASE|SNAPSHOT).*"), "固定でない版: " + version);
            assertFalse(version.matches(".*[\\[\\](),].*"), "範囲の版: " + version);
            assertFalse(version.isEmpty());
        }
        // 版を持たない dependency や plugin がない（親や管理の仕組みに任せない）
        for (Matcher matcher = Pattern.compile("<(dependency|plugin)>(.*?)</\\1>", Pattern.DOTALL).matcher(pom); matcher.find();) {
            assertTrue(matcher.group(2).contains("<version>"), "版がない: " + matcher.group(2).replaceAll("\\s+", " ").trim());
        }
    }

    @Test
    void onlyTheExpectedLibrariesAreUsed() throws IOException {
        String pom = read("pom.xml");
        List<String> found = new ArrayList<>();

        for (Matcher matcher = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL).matcher(pom); matcher.find();) {
            String block = matcher.group(1);
            found.add(tag(block, "groupId") + ":" + tag(block, "artifactId"));
        }

        for (String dependency : found) {
            assertTrue(ALLOWED_DEPENDENCIES.contains(dependency), "許可していない依存: " + dependency);
        }
        assertTrue(found.size() <= ALLOWED_DEPENDENCIES.size(), "依存の数を、少なく保つ");
    }

    private static String tag(String block, String name) {
        Matcher matcher = Pattern.compile("<" + name + ">([^<]*)</" + name + ">").matcher(block);
        assertTrue(matcher.find(), name + " がない");
        return matcher.group(1).trim();
    }

    // ---- コンテナ ----

    @Test
    void baseImagesArePinnedByVersionAndDigestAndTheAppRunsAsNonRoot() throws IOException {
        String dockerfile = read("Dockerfile");

        List<String> images = new ArrayList<>();
        for (Matcher matcher = Pattern.compile("(?m)^FROM\\s+(\\S+)").matcher(dockerfile); matcher.find();) {
            images.add(matcher.group(1));
        }
        assertEquals(2, images.size(), "ビルドと実行の 2 段");
        for (String image : images) {
            assertFalse(image.contains(":latest"), "latest は使わない: " + image);
            assertTrue(image.matches(".+:\\d[^@\\s]*@sha256:[0-9a-f]{64}"), "版とダイジェストで固定する: " + image);
        }
        assertTrue(dockerfile.matches("(?s).*\\nUSER\\s+(?!root\\b)\\S+.*"), "実行は、root 以外の利用者で行う");
        assertFalse(dockerfile.matches("(?is).*\\nADD\\s+https?://.*"), "URL からの ADD は使わない");
        assertFalse(dockerfile.matches("(?im)^(ENV|ARG)\\s+\\S*(KEY|SECRET|PASSWORD|TOKEN)\\S*\\s*=.*"), "鍵を、イメージに入れない");
    }

    @Test
    void tomcatConfigurationClosesTheShutdownPortAndHidesErrorDetails() throws IOException {
        String serverXml = read("docker/server.xml");

        assertTrue(serverXml.contains("<Server port=\"-1\""), "停止用のポートを閉じる");
        assertTrue(serverXml.contains("showServerInfo=\"false\""));
        assertTrue(serverXml.contains("showReport=\"false\""));
        assertTrue(serverXml.contains("autoDeploy=\"false\""), "実行中に、アプリを追加できない");
        assertTrue(serverXml.contains("maxPostSize="), "送れる大きさに上限を置く");
        assertTrue(serverXml.contains("${http.port}"), "待ち受けるポートは、Cloud Run の PORT から");
    }

    // ---- デプロイ用のスクリプト ----

    private static List<Path> deployScripts() throws IOException {
        Path folder = Path.of("deploy");
        Assumptions.assumeTrue(Files.isDirectory(folder), "deploy がないので飛ばす");
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(path -> path.toString().endsWith(".ps1")).toList();
        }
    }

    @Test
    void deployScriptsAreAsciiSoWindowsPowerShell51ReadsThemCorrectly() throws IOException {
        for (Path script : deployScripts()) {
            for (byte value : Files.readAllBytes(script)) {
                assertTrue(value >= 0, script + " に、ASCII 以外の文字がある（PowerShell 5.1 が壊す）");
            }
        }
    }

    @Test
    void deployDoesNotGrantBroadRolesAndLimitsInstancesAndUsesADedicatedAccount() throws IOException {
        String deploy = read("deploy/deploy.ps1");

        assertFalse(deploy.matches("(?is).*roles/(owner|editor|[a-z.]*admin)\\b.*"), "広い権限を与えない");
        assertTrue(deploy.contains("--max-instances"), "インスタンス数に上限を置く");
        assertTrue(deploy.contains("--service-account"), "専用のサービスアカウントで動かす");
        assertTrue(deploy.contains("--set-secrets"), "鍵は Secret Manager から");
        assertTrue(deploy.contains("containerscanning.googleapis.com"), "イメージの脆弱性の検査を有効にする");
        assertTrue(deploy.contains("--retention-days"), "ログの保持期間を決める");
        assertTrue(deploy.contains("roles/secretmanager.secretAccessor"));
        assertTrue(deploy.contains("if ($AllowUnauthenticated)"), "公開は、指定したときだけ");
    }

    // ---- リポジトリに鍵がないこと ----

    @Test
    void noSecretIsCommittedToTheRepository() throws IOException {
        Pattern secret = Pattern.compile("AIza[0-9A-Za-z_\\-]{35}|-----BEGIN [A-Z ]*PRIVATE KEY-----|ya29\\.[0-9A-Za-z_\\-]{20,}"
                + "|sk-[A-Za-z0-9]{32,}|gh[pousr]_[A-Za-z0-9]{36,}|AKIA[0-9A-Z]{16}");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("."))) {
            for (Path file : files.filter(Files::isRegularFile).filter(SupplyChainPolicyTest::isScanned).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (secret.matcher(text).find()) {
                    hits.add(file.toString());
                }
            }
        }
        assertTrue(hits.isEmpty(), "鍵のような文字列がある: " + hits);
    }

    private static boolean isScanned(Path file) {
        String path = file.toString().replace('\\', '/');
        if (path.contains("/target/") || path.contains("/.git/") || path.contains("/node_modules/")) {
            return false;
        }
        // 鍵の形を、検査用に書いてあるこのテストと、評価用のテストは、対象から外す
        if (path.endsWith("SupplyChainPolicyTest.java") || path.endsWith("GuardEvaluationTest.java")) {
            return false;
        }
        String name = file.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".jsp") || name.endsWith(".tag") || name.endsWith(".js")
                || name.endsWith(".xml") || name.endsWith(".ps1") || name.endsWith(".json") || name.endsWith(".md")
                || name.endsWith(".properties") || name.equals("Dockerfile") || name.endsWith(".rules");
    }
}
