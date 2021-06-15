import org.opencv.core.Mat;
import origami.Filter;
import origami.Origami;
import origami.filters.NoOPFilter;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;

public class Loomy {
    static final boolean DEBUG = false;
    static final boolean WRITE = true;
    static final int DEFAULT_MODE = 1;
    private static Filter filter;

    /**
     * Run this with parameters like:
     * <test-case> <filter-string>
     * 1 "{:class origami.filters.Cartoon2}]"
     *
     * Where test-case:
     * 1: Virtual Threads
     * 2: Standard Threads
     * 3: No Threads (loop)
     * 4: ForkJoin
     * 5: Virtual Threads and Priority
     *
     * Defaults are: Virtual Threads, NoOPFilter
     */
    public static void main(String[] args) throws InterruptedException {
        int mode = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_MODE;
        filter = args.length > 1 ? Origami.StringToFilter(args[1]) : new NoOPFilter();

        Origami.init();

        File[] images = Objects.requireNonNull(Paths.get("images").toFile().listFiles((dir, name) -> name.endsWith("png") | name.endsWith("jpg")));

        var start = System.currentTimeMillis();
        switch (mode) {
            case 1:
                System.out.println("loomyVirtualThreads");
                loomyVirtualThreads(images);
                break;
            case 2:
                System.out.println("loomyStandardThreads");
                loomyStandardThreads(images);
                break;
            case 3:
                System.out.println("loomyNoThreads");
                loomyNoThreads(images);
                break;
            case 4:
                System.out.println("loomyForkJoin");
                loomyForkJoin(images);
                break;
            case 5:
                System.out.println("loomyVirtualThreadsSplitArrayAndPriority");
                loomyVirtualThreadsSplitArrayAndPriority(images);
                break;
            default:
                System.out.println("Default. Invalid test case");
                break;
        }

        var end = System.currentTimeMillis();
        System.out.printf("Time [%d ms] / Images [ %d ] / AVG [%d images/sec] \n", end - start, images.length, 1000 * images.length / (end - start));

    }

    private static void loomyVirtualThreadsSplitArrayAndPriority(File[] images) throws InterruptedException {
        int n = images.length;
        File[] i1 = Arrays.copyOfRange(images, 0, (n + 1) / 2);
        File[] i2 = Arrays.copyOfRange(images, (n + 1) / 2, n);

        Thread t = new Thread(() -> {
            try {
                loomyVirtualThreadsWithPriority(i1, 1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                loomyVirtualThreadsWithPriority(i2, 10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        t.start();
        t2.start();

        t.join();
        t2.join();
    }

    static String process(File image) {
        Mat m = filter.apply(imread(image.getAbsolutePath()));
        String path = "target/" + image.getName();
        if (WRITE)
            imwrite(path, m);
        if (DEBUG)
            System.out.printf("Processed image ... [%s]\n", path);
        return path;
    }

    static class OrigamiTaskAsRunnable implements Runnable {

        private final File image;

        public OrigamiTaskAsRunnable(File image) {
            this.image = image;
        }

        public void run() {
            process(image);
        }
    }

    static class OrigamiTaskAsRecursiveTask extends RecursiveTask<String> {

        private File image;

        public OrigamiTaskAsRecursiveTask(File image) {
            this.image = image;
        }

        @Override
        public String compute() {
            return process(image);
        }

    }

    private static void loomyStandardThreads(File[] images) throws InterruptedException {
        var listOfThreads = new ArrayList<Thread>(images.length);
        for (int i = 0; i < images.length; i++) {
            var t = new Thread(new OrigamiTaskAsRunnable(images[i]));
            listOfThreads.add(t);
            t.start();
        }
        for (int i = 0; i < images.length; i++) {
            listOfThreads.get(i).join();
        }
    }

    private static void loomyNoThreads(File[] images) {
        for (File image : images) {
            new OrigamiTaskAsRunnable(image).run();
        }
    }

    private static void loomyVirtualThreads(File[] images) throws InterruptedException {
        var listOfThreads = new ArrayList<Thread>(images.length);
        for (int i = 0; i < images.length; i++) {
            var t = Thread.startVirtualThread(new OrigamiTaskAsRunnable(images[i]));
            listOfThreads.add(t);
        }
        for (int i = 0; i < images.length; i++) {
            listOfThreads.get(i).join();
        }
    }


    private static void loomyForkJoin(File[] images) {
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        for (int i = 0; i < images.length; i++) {
            OrigamiTaskAsRecursiveTask task = new OrigamiTaskAsRecursiveTask(images[i]);
            forkJoinPool.execute(task);
        }

        forkJoinPool.shutdown();
        try {
            forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void loomyVirtualThreadsWithPriority(File[] images, int priority) throws InterruptedException {
        long start = System.currentTimeMillis();
        var listOfThreads = new ArrayList<Thread>(images.length);
        for (int i = 0; i < images.length; i++) {
            var t = Thread.startVirtualThread(new OrigamiTaskAsRunnable(images[i]));
            t.setPriority(priority);
            listOfThreads.add(t);
        }

        for (int i = 0; i < images.length; i++) {
            listOfThreads.get(i).join();
        }
        long end = System.currentTimeMillis();
        System.out.printf("Processing with priority: %d finished. [%d images] [%d ms] \n", priority, images.length, end - start);
    }

}
