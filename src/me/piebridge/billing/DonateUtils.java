package me.piebridge.billing;

import android.text.TextUtils;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collection;

import me.piebridge.prevent.ui.UILog;

/**
 * Created by thom on 15/10/11.
 */
public class DonateUtils {

    public static final int REQUEST_CODE = 0x1000;

    public static final int API_VERSION = 3;

    public static final String ITEM_ID = "donate";

    public static final String ITEM_TYPE = "inapp";

    // @formatter:off
    private static final byte[] MODULUS = {
             -49,   46,   -4,   49,  107,   86, -118,   15,  -77,    4,  -19,   58,  104,  -43,   -8,   83,
             -62,   21,   -2,  -78,    3,   37,   80,    1, -107,   96,    9,  -60,   57,  -58,  -53,  -14,
              52,   71, -109,  -92,   38,   92, -120,   75,    3,  -53,  -95,   39,   85,   24,   87,  -69,
             113,   42,  -73,  -59,   31,  -94,  -14,   20,  -19,  -70,   22,   80, -114,  -47,   98,    8,
             -28,  109,   80,   77,  -52,   84, -113,  -66,   72,  120,  -52,   73,  -70, -108,   -7, -122,
             -70, -118,  -86,   84, -124,   91,   90,  -98,   75,  -44,   -2,  -10,  -88,  -50, -121,  -63,
             -34, -107,  -17,  -96,   -5,   24,  -80,  -16,  127,   20,    0,    3,   -7,   58,   46,  -44,
             -76,   54,  -57,   85,  -10,   -6, -123,   -6,  -35,  126, -111,  -62,   31,  102,   54,  -20,
              88,  -58,   30,  -42,   32,  100,   -6,  108,  -66,  -11,  -63,  -90, -127,   66,  108,  -33,
             -24,  -92, -107, -108,  -65,  100,  -52,   43,  -55,    4,  -99,   93,   -1,  111,  104, -108,
             116,  106,  -99,  -86,  -24,  -51,   -6,  -55,   91, -108,   79,  -46,   37,   37,   72,  -33,
              66,  107,   70,   67,  -94,  -21,  111,    8,   33,  -15,  -57,  -85,   58,  -33,   56,  121,
             -36,   92,   73,    2,  -41,  -91,   49, -124,   23, -103,  -83,   -1,   59,  -80,   -1,  -55,
             116,  -11,  -91,  118,  -16,   36,  -31,  -45, -127,  117,   99,   97, -110, -128,   12,  -84,
              87, -127,  -50,  -87,  -75,   -6,   11,   60,  -50,  100,  -26,  109,   73, -105,   77,  -52,
              35,  -98,  -59,  -35,  -62,  -52,   46,   63, -105,   -1,   84, -125,   15,   98, -109,   43,
    };
    // @formatter:on

    private DonateUtils() {

    }

    public static boolean isEmpty(Collection<String> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean verify(String data, String signature) {
        if (TextUtils.isEmpty(data) || TextUtils.isEmpty(signature)) {
            return false;
        }
        BigInteger exponent = BigInteger.valueOf(0x10001);
        BigInteger modulus = new BigInteger(1, MODULUS);
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(data.getBytes());
            byte[] plain = getSignature(sha1.digest());
            byte[] key = Base64.decode(signature, Base64.DEFAULT);
            byte[] sign = new BigInteger(1, key).modPow(exponent, modulus).toByteArray();
            return equals(plain, sign);
        } catch (IllegalArgumentException e) {
            UILog.e("illegal argument exception", e);
        } catch (GeneralSecurityException e) {
            UILog.e("security exception", e);
        }
        return false;
    }

    private static byte[] getSignature(byte[] sha1) {
        // rfc3447, sha-1
        byte[] algorithm = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
        ByteArrayOutputStream signature = new ByteArrayOutputStream(0xff);
        signature.write(0x01);
        // 0xff - 2 - algorithm - sha1
        for (int i = 0; i < 0xda; ++i) {
            signature.write(0xff);
        }
        signature.write(0x00);
        signature.write(algorithm, 0, algorithm.length);
        signature.write(sha1, 0, sha1.length);
        return signature.toByteArray();
    }

    private static boolean equals(byte[] a, byte[] b) { // NOSONAR
        int length = a.length;
        if (length != b.length) {
            return false;
        }

        for (int i = 0; i < length; ++i) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

}
