import com.android.apksig.ApkVerifier;

import java.io.File;

/** Checks an APK's v1 and v2 signatures with Google's apksig verifier. */
public class VerifyApk {
    public static void main(String[] args) throws Exception {
        ApkVerifier.Result r = new ApkVerifier.Builder(new File(args[0])).setMinCheckedPlatformVersion(24).build().verify();
        System.out.println("verified=" + r.isVerified() + " v1=" + r.isVerifiedUsingV1Scheme() + " v2=" + r.isVerifiedUsingV2Scheme());
        for (ApkVerifier.IssueWithParams e : r.getErrors()) System.out.println("ERROR " + e);
        for (ApkVerifier.IssueWithParams w : r.getWarnings()) System.out.println("WARNING " + w);
        if (!r.isVerified()) System.exit(1);
    }
}
