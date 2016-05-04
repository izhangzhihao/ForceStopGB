package me.piebridge.prevent.framework;

import android.content.IntentFilter;

/**
 * Created by thom on 15/7/12.
 */
public final class IntentFilterMatchResult {

    private Class<?> type;
    private Integer result;

    public static final IntentFilterMatchResult NONE = new IntentFilterMatchResult(Void.class, null);
    public static final IntentFilterMatchResult NO_MATCH = new IntentFilterMatchResult(int.class, IntentFilter.NO_MATCH_ACTION);

    private IntentFilterMatchResult(Class<?> type, Integer result) {
        this.type = type;
        this.result = result;
    }

    public boolean isNone() {
        return Void.class.equals(this.type);
    }

    public Integer getResult() {
        return this.result;
    }

}
