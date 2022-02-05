package android.util;

public class Log {
    public static int d(String tag, String msg) {
        System.out.printf("D/%s: %s\n", tag, msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        System.out.printf("I/%s: %s\n", tag, msg);
        return 0;
    }

    public static int w(String tag, String msg) {
        System.out.printf("W/%s: %s\n", tag, msg);
        return 0;
    }

    public static int e(String tag, String msg) {
        System.out.printf("E/%s: %s\n", tag, msg);
        return 0;
    }
}
