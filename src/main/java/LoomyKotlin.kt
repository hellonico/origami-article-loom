import kotlin.Throws
import java.lang.InterruptedException
import kotlin.jvm.JvmStatic
import origami.Origami
import origami.filters.NoOPFilter
import java.io.File
import java.util.Objects
import java.nio.file.Paths
import java.io.FilenameFilter
import java.util.Arrays
import java.lang.Runnable
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import origami.Filter
import java.util.ArrayList
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.RecursiveTask

object LoomyKotlin {
    const val DEBUG = false
    const val WRITE = true
    const val DEFAULT_MODE = 1
    private var filter: Filter? = null

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
    </filter-string></test-case> */
    @Throws(InterruptedException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val mode = if (args.size > 0) args[0].toInt() else DEFAULT_MODE
        filter = if (args.size > 1) Origami.StringToFilter(args[1]) else NoOPFilter()
        Origami.init()
        val images = Objects.requireNonNull(
            Paths.get("images").toFile()
                .listFiles { _: File?, name: String -> name.endsWith("png") or name.endsWith("jpg") })
        val start = System.currentTimeMillis()
        when (mode) {
            1 -> {
                println("loomyVirtualThreads")
                loomyVirtualThreads(images)
            }
            2 -> {
                println("loomyStandardThreads")
                loomyStandardThreads(images)
            }
            3 -> {
                println("loomyNoThreads")
                loomyNoThreads(images)
            }
            4 -> {
                println("loomyForkJoin")
                loomyForkJoin(images)
            }
            5 -> {
                println("loomyVirtualThreadsSplitArrayAndPriority")
                loomyVirtualThreadsSplitArrayAndPriority(images)
            }
            else -> println("Default. Invalid test case")
        }
        val end = System.currentTimeMillis()
        System.out.printf(
            "Time [%d ms] / Images [ %d ] / AVG [%d images/sec] \n",
            end - start,
            images.size,
            1000 * images.size / (end - start)
        )
    }

    @Throws(InterruptedException::class)
    private fun loomyVirtualThreadsSplitArrayAndPriority(images: Array<File>) {
        val n = images.size
        val i1 = Arrays.copyOfRange(images, 0, (n + 1) / 2)
        val i2 = Arrays.copyOfRange(images, (n + 1) / 2, n)
        val t = Thread {
            try {
                loomyVirtualThreadsWithPriority(i1, 1)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        val t2 = Thread {
            try {
                loomyVirtualThreadsWithPriority(i2, 10)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        t.start()
        t2.start()
        t.join()
        t2.join()
    }

    fun process(image: File): String {
        val m = filter!!.apply(Imgcodecs.imread(image.absolutePath))
        val path = "target/" + image.name
        if (WRITE) Imgcodecs.imwrite(path, m)
        if (DEBUG) System.out.printf("Processed image ... [%s]\n", path)
        return path
    }

    @Throws(InterruptedException::class)
    private fun loomyStandardThreads(images: Array<File>) {
        val listOfThreads = ArrayList<Thread>(images.size)
        for (i in images.indices) {
            val t = Thread(OrigamiTaskAsRunnable(images[i]))
            listOfThreads.add(t)
            t.start()
        }
        for (i in images.indices) {
            listOfThreads[i].join()
        }
    }

    private fun loomyNoThreads(images: Array<File>) {
        for (image in images) {
            OrigamiTaskAsRunnable(image).run()
        }
    }

    @Throws(InterruptedException::class)
    private fun loomyVirtualThreads(images: Array<File>) {
        val listOfThreads = ArrayList<Thread>(images.size)
        for (i in images.indices) {
            val t = Thread.startVirtualThread(OrigamiTaskAsRunnable(images[i]))
            listOfThreads.add(t)
        }
        for (i in images.indices) {
            listOfThreads[i].join()
        }
    }

    private fun loomyForkJoin(images: Array<File>) {
        val forkJoinPool = ForkJoinPool()
        for (i in images.indices) {
            val task = OrigamiTaskAsRecursiveTask(images[i])
            forkJoinPool.execute(task)
        }
        forkJoinPool.shutdown()
        try {
            forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @Throws(InterruptedException::class)
    private fun loomyVirtualThreadsWithPriority(images: Array<File>, priority: Int) {
        val start = System.currentTimeMillis()
        val listOfThreads = ArrayList<Thread>(images.size)
        for (i in images.indices) {
            val t = Thread.startVirtualThread(OrigamiTaskAsRunnable(images[i]))
            t.priority = priority
            listOfThreads.add(t)
        }
        for (i in images.indices) {
            listOfThreads[i].join()
        }
        val end = System.currentTimeMillis()
        System.out.printf(
            "Processing with priority: %d finished. [%d images] [%d ms] \n",
            priority,
            images.size,
            end - start
        )
    }

    internal class OrigamiTaskAsRunnable(private val image: File) : Runnable {
        override fun run() {
            process(image)
        }
    }

    internal class OrigamiTaskAsRecursiveTask(private val image: File) : RecursiveTask<String>() {
        public override fun compute(): String {
            return process(image)
        }
    }
}