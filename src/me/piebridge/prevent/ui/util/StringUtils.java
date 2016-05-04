package me.piebridge.prevent.ui.util;

/**
 * Created by thom on 16/1/28.
 */
public class StringUtils {

    private static final char[] WHITESPACE = ("\u0085\u00a0\u1680"
            + "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a"
            + "\u2028\u2029\u202f\u205f\u3000").toCharArray();

    private StringUtils() {

    }

    private static boolean isWhiteSpace(char s) {
        if (s <= ' ') {
            return true;
        }
        for (char c : WHITESPACE) {
            if (c == s) {
                return true;
            }
        }
        return false;
    }

    public static CharSequence trim(CharSequence cs) {
        if (cs != null) {
            int last = cs.length() - 1;
            int start = 0;
            int end = last;
            while (start <= end && isWhiteSpace(cs.charAt(start))) {
                ++start;
            }
            while (end >= start && isWhiteSpace(cs.charAt(end))) {
                --end;
            }
            if (start != 0 || end != last) {
                return cs.subSequence(start, end + 1);
            }
        }
        return cs;
    }

}
