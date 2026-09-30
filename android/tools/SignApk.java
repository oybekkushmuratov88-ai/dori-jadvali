import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Signs an APK (v2 scheme; minSdk 24 needs no v1) with a PKCS12 key, then verifies the result. */
public class SignApk {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("usage: SignApk <keystore.p12> <alias> <password> <in.apk> <out.apk>");
            System.exit(2);
        }
        char[] pass = args[2].toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(args[0])) {
            ks.load(in, pass);
        }
        PrivateKey key = (PrivateKey) ks.getKey(args[1], pass);
        X509Certificate cert = (X509Certificate) ks.getCertificate(args[1]);
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder("dori", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(new File(args[3]))
                .setOutputApk(new File(args[4]))
                .setMinSdkVersion(24)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .build()
                .sign();

        ApkVerifier.Result r = new ApkVerifier.Builder(new File(args[4])).build().verify();
        System.out.println("verified=" + r.isVerified() + " v1=" + r.isVerifiedUsingV1Scheme() + " v2=" + r.isVerifiedUsingV2Scheme());
        for (ApkVerifier.IssueWithParams e : r.getErrors()) System.out.println("ERROR " + e);
        if (!r.isVerified()) System.exit(1);
    }
}
