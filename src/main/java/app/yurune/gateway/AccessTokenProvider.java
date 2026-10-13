package app.yurune.gateway;

/**
 * Google Cloud の API を呼ぶときの、アクセストークンの取り出し口。
 * Cloud Run では、実行しているサービスアカウントのトークンを、メタデータサーバーから得る（鍵のファイルを持たない）。
 */
public interface AccessTokenProvider {

    /** 取れないときは、StorageUnavailableException ではなく IllegalStateException にする（設定の誤りを見つけやすくするため）。 */
    String getToken();
}
