package com.brainzsquare.bcommons.os

import com.brainzsquare.bcommons.io.IOUtil
import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.error.onNegative
import com.brainzsquare.bcommons.misc.TimeUtil
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


object ProcessUtil {
    fun start(err: MyErr, args: Array<out String>) : Process? {
        return try {
            Runtime.getRuntime().exec(args)
        } catch (t: Throwable) {
            err.newErrUnexpected(
                "exception while starting process. cmdLine: " + args.joinToString(" "),
                exception = t)
            null
        }
    }

    fun startAndWait(err: MyErr, args: Array<out String>) : Process? {
        val process = start(err, args)
            ?: run {
                err.addLocation(object{})
                return null
            }
        waitEndOrDestroy(err, process).onNegative {
            err.addLocation(object{})
            return null
        }
        return process
    }

    fun runAndCapture(err: MyErr, args: Array<out String>, wait: Long = this.wait * this.waitCount): String? {
        val process = start(err, args)
            ?: run {
                err.addLocation(object{})
                return null
            }
        val resultLock = Any()
        var resultStr: String? = null
        val resultErr = MyErr()
        val th = thread(true, false) {
            val s = IOUtil.readAllAsUTF8String(resultErr, process.inputStream)
                ?: run {
                    resultErr.addLocation(object{})
                    null
                }
            synchronized(resultLock) {
                resultStr = s
            }
        }
        try {
            th.join(wait)
        } catch (e: InterruptedException) {
            err.newErr(MyErr.etInterrupted, locObj = object{})
            return null
        }
        val rc = waitEndOrDestroy(err, process)
        if (rc != 1) {
            err.newErrUnexpected("waitEndOrDestroy failed. rc: $rc", locObj = object{})
            return null
        }
        synchronized(resultLock) {
            if (resultErr.isError) {
                err.copyFrom(resultErr)
                err.addMsg("error while reading output of the process")
                err.addLocation(object{})
                return null
            } else if (resultStr == null) {
                err.newErrUnexpected("output of the process is null", locObj = object{})
                return null
            }
            return resultStr
        }
    }

    // 1            OK
    // 2            destroyed forcefully
    // -1           cannot destroy
    // ecUnexpected unexpected exception
    fun destroy(err: MyErr, process: Process, wait: Long = this.wait, waitCount: Int = this.waitCount) : Int {
        try {
            if (!process.isAlive) return 1
            process.destroy()
            if (!process.isAlive) return 1
            for (i in 0 until waitCount) {
                TimeUtil.sleep(err, wait)
                if (!process.isAlive) return 1
            }

            process.destroyForcibly()
            if (!process.isAlive) return 2
            for (i in 0 until waitCount) {
                TimeUtil.sleep(err, wait)
                if (!process.isAlive) return 2
            }

            val total = wait * waitCount
            err.newErrUnexpected("the process $process still alive after $total msec", locObj = object{})
            return err.errCode
        } catch (t: Throwable) {
            err.newErrUnexpected("while destroying the process $process", exception = t, locObj = object{})
            return err.errCode
        }
    }

    // 1 ended ok
    // 2 destroyed
    // 3 destroyed forcefully
    fun waitEndOrDestroy(err: MyErr, process: Process, wait: Long = this.wait, waitCount: Int = this.waitCount) : Int {
        if (process.waitFor(wait*waitCount, TimeUnit.MILLISECONDS)) return 1

        return when (val rc = destroy(err, process, wait, waitCount)) {
            1 -> 2
            2 -> 3
            else -> {
                err.addLocation(object{})
                rc
            }
        }
    }

//    fun logErrorStream(logger: Logger?, p: Process) {
//        if (p.isAlive) {
//            LogUtil.info(logger, "the process {} is still alive", p)
//            return
//        }
//        LogUtil.info(logger, "error from the process {}", p)
//		IOUtil.withLinedStream(logger, p.errorStream) {
//			LogUtil.info(logger, "{}{}", LogUtil.CONTINUE_PREFIX, it)
//		}
//    }

    private const val wait = 1000L
    private const val waitCount = 5

}
