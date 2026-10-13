package app.yurune.gateway;

import app.yurune.service.AuthAccount;
import app.yurune.service.AuthException;
import app.yurune.service.AuthGateway;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * メモリ上の認証。開発・テスト用で、再起動すると消える。本番では使わない（Firebase Authentication を使う）。
 * パスワードは、そのままでは持たず、ソルトつきの PBKDF2 で保存する。メール確認は、省く（すぐに確認済みになる）。
 */
public final class InMemoryAuthGateway implements AuthGateway {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int UID_BYTES = 12;

    private record StoredAccount(String uid, String email, byte[] salt, byte[] hash) {
    }

    private final Map<String, StoredAccount> accounts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public AuthAccount register(String email, String password) {
        String key = email.toLowerCase(Locale.ROOT);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] uidBytes = new byte[UID_BYTES];
        random.nextBytes(uidBytes);
        StoredAccount created = new StoredAccount("m" + HexFormat.of().formatHex(uidBytes), key, salt, hash(password, salt));
        if (accounts.putIfAbsent(key, created) != null) {
            throw new AuthException(AuthException.Reason.EMAIL_ALREADY_USED);
        }
        return new AuthAccount(created.uid(), key, true);
    }

    @Override
    public AuthAccount signIn(String email, String password) {
        StoredAccount stored = accounts.get(email.toLowerCase(Locale.ROOT));
        // 存在しないメールでも、ハッシュ計算の時間をかけて、存在の有無を、応答の速さから推測されないようにする
        byte[] salt = stored != null ? stored.salt() : new byte[SALT_BYTES];
        byte[] computed = hash(password, salt);
        if (stored == null || !MessageDigest.isEqual(stored.hash(), computed)) {
            throw new AuthException(AuthException.Reason.INVALID_CREDENTIALS);
        }
        return new AuthAccount(stored.uid(), stored.email(), true);
    }

    private byte[] hash(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new AuthException(AuthException.Reason.UNAVAILABLE, exception);
        }
    }
}
