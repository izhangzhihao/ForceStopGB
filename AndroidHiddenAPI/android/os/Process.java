package android.os;

public class Process {

    public static final int SYSTEM_UID = 1000;

    /**
      * @hide
      */
    public static final void readProcLines(String path, String[] reqFields, long[] outSizes) {
    }

    /**
      * @hide
      */
    public static final int getUidForPid(int pid) {
        throw new UnsupportedOperationException();
    }

    public static final void killProcess(int pid) {
    }

    public static final int myUid() {
        throw new UnsupportedOperationException();
    }

    public static final int myPid() {
        throw new UnsupportedOperationException();
    }

    public static final int myTid() {
        throw new UnsupportedOperationException();
    }

}
