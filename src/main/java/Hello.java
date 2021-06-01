import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import origami.Origami;
import origami.filters.Cartoon2;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;

import static org.opencv.imgcodecs.Imgcodecs.imwrite;

public class Hello {

    public static void main(String[] args) throws InterruptedException {
        System.out.println(System.getProperties().toString());
//        System.setProperty("os.arch", "x86_64");
        System.setProperty("os.arch", "arm_64");
        System.out.println(System.getProperty("os.arch"));
//        System.setProperty("os.arch", "arm64");
        Origami.init();

        var start = System.currentTimeMillis();
        // loom(100000);
        File[] images = Paths.get("images").toFile().listFiles((dir, name) -> name.endsWith("png")|name.endsWith("jpg"));
//        loomyVirtualThreads(images);


        var end = System.currentTimeMillis();
        System.out.printf("Time [%d ms]\n", end-start);
    }

    private static void loomyStandardThreads(File[] images) throws InterruptedException {
        var listOfThreads = new ArrayList<Thread>(images.length);
        for(int i = 0 ; i < images.length ; i++) {
            int finalI = i;
            var r = new Runnable() {
                @Override
                public void run() {
                    System.out.printf("Hello, Loom! %d\n [%s]", finalI, images[finalI]);
                    Mat m = new Cartoon2().apply(Imgcodecs.imread(images[finalI].getAbsolutePath()));
                    imwrite("target/"+images[finalI].getName(),m);
                }
            };
            var t = new Thread(r);
            t.start();
            listOfThreads.add(t);
        }
        for(int i = 0 ; i < images.length ; i++) {
            listOfThreads.get(i).join();
        }
    }

    private static void loomyVirtualThreads(File[] images) throws InterruptedException {
        var listOfThreads = new ArrayList<Thread>(images.length);
        for(int i = 0 ; i < images.length ; i++) {
            int finalI = i;
            var t = Thread.startVirtualThread(() -> {
                System.out.printf("Hello, Loom! %d\n [%s]", finalI, images[finalI]);
            });
            listOfThreads.add(t);
        }
        for(int i = 0 ; i < images.length ; i++) {
            listOfThreads.get(i).join();
        }
    }
}
