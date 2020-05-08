public class WaitUtils {
    public static void waitFor(int ms) {
        try {
            Thread.sleep(ms);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
