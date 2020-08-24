import com.jackvanlightly.rabbittesttool.statistics.MetricCounter;
import com.jackvanlightly.rabbittesttool.statistics.MetricType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;


public class MetricCounterTest {
    @Test
    public void threadSafetyTest() {
        MetricCounter mc = new MetricCounter(MetricType.PublisherSentMessage);
        Runnable r1 = () -> {
            for(int i=0; i<Integer.MAX_VALUE; i++) {
                mc.increment();
                if(i % 100000 == 0) {
                    System.out.println("Incremented: " + i);
                }
            }

            System.out.println("FINISHED");
        };
        Thread thread = new Thread(r1);
        thread.start();

        long sum = 0;
        long lastPrinted = 0;
        Instant lastPrintedTs = Instant.now();
        while(sum < Integer.MAX_VALUE) {
            sum+= mc.getRealDeltaValue();

            Instant now = Instant.now();

            if(sum == Integer.MAX_VALUE)
                break;
            else if(Duration.between(lastPrintedTs, now).getSeconds() > 10) {
                if(sum == lastPrinted)
                    break;

                System.out.println("READ: " + sum);
                lastPrinted = sum;
                lastPrintedTs = now;
            }
            else if(sum - lastPrinted > 100000) {
                System.out.println("READ: " + sum);
                lastPrinted = sum;
            }

        }

        try {
            thread.join();
        }
        catch (InterruptedException e) {}

        long expected = (long)Integer.MAX_VALUE;
        System.out.print("Sum: " + sum + " expected: " + expected);
        assertThat(sum == expected);
    }
}
