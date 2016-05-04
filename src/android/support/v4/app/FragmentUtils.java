package android.support.v4.app;

/**
 * Created by thom on 15/7/24.
 */
public class FragmentUtils {

    private FragmentUtils() {

    }

    public static void setTag(Fragment fragment, String tag) {
        if (fragment != null) {
            fragment.mTag = tag;
        }
    }
}
