package com.brainzsquare.bcommons.os

import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.error.onNegative
import com.brainzsquare.bcommons.misc.TimeUtil
import java.io.File
import java.lang.reflect.Field


object LinuxUtil {
    private val pidField: Field by lazy {
        val f = Thread.currentThread().contextClassLoader.loadClass("java.lang.UNIXProcess")!!
            .getDeclaredField("pid")!!
        f.isAccessible = true
        f
    }

    fun getPid(p: Process): Long {
        return this.pidField.getLong(p)
    }

    /**
     * waits for a while till pid finishes
     * @return @code{1}
     */
    fun waitProcessDone(err: MyErr, pid: Long, wait: Long = 1000L, waitCount: Int = 5): Int {
        val procStatFileName = "/proc/$pid/stat"
        for (i in 0 until waitCount) {
            val exists = try {
                File(procStatFileName).exists()
            } catch (t: Throwable) {
                err.newErr(MyErr.etUnexpected, exception = t)
                continue
            }
            if (! exists) return 0
            if (i < waitCount - 1) {
                TimeUtil.sleep(err, wait)
                    .onNegative {
                        err.addLocation(object {})
                        return err.errCode
                    }
            }
        }
        return 1
    }

    /**
     * @return
     * 1 kill OK
     * 2 kill -9 OK
     * <0 error
     */
    fun killProcess(err: MyErr, pid: Long): Int {
        val process = ProcessUtil.startAndWait(err, arrayOf("kill", pid.toString()))
            ?: run {
                err.addLocation(object{})
                return err.errCode
            }
        if (process.exitValue() == 0) return 1
        // may not have been killed
        val process2 = ProcessUtil.startAndWait(err, arrayOf("kill", "-9", pid.toString()))
            ?: run {
                err.addLocation(object{})
                return err.errCode
            }
        if (process2.exitValue() == 0) return 2
        err.newErrUnexpected("kill and kill -9 failed. pid: $pid")
        return err.errCode
    }

//    fun findPgid(err: MyErr, process: Process): Long {
//        val pid = getPid(process).onNegative {
//            err.addLocation(object{})
//            return err.errCode.toLong()
//        }
//        val procStatFileName = "/proc/$pid/stat"
//        val content = IOUtil.readAllAsUTF8String(err, File(procStatFileName).inputStream())
//            ?: run {
//                err.addLocation(object{})
//                return err.errCode.toLong()
//            }
//        val words = content.split(' ')
//        if (words.size < 5) {
//            err.newErrUnexpected("words.size < 5. $procStatFileName. content: $content")
//            return err.errCode.toLong()
//        }
//        return words[4].toLong(err).onNegative {
//            if (! err.isError) {
//                err.newErrUnexpected("words[4].toLong failed. $procStatFileName. content: $content")
//            }
//            err.addLocation(object{})
//            return err.errCode.toLong()
//        }
//    }

//    fun findPgidByName(err: MyErr, name: String) : Long {
//        val p = ProcessUtil.startAndWait(err, arrayOf("pidof", name)
//            ?: return -11L
//        val list = p.inputStream.use {
//            runCatching {
//                val list = IOUtil.readAllAsUTF8String(logger, it).split(' ')
//                if (list.isEmpty()) return -1L
//                list
//            }.onFailure {
//                LogUtil.unexpected(logger, it)
//                return -12L
//            }.getOrNull()!!
//        }
//        for (pidStr in list) {
//            if (pidStr.isEmpty()) continue
//            val l = runCatching {
//                val ss = IOUtil.readAllAsUTF8String(
//					logger,
//					File("/proc/$pidStr/stat").inputStream()
//				)
//                val l = ss.split(' ')
//                if (l.size < 4) {
//                    logger?.debug("pid: {} {} {}", pidStr, l.size, ss)
//                    return -20L
//                }
//                l
//            }.onFailure {
//                LogUtil.unexpected(logger, it)
//                return -21L
//            }.getOrNull()!!
//            val pgid = runCatching { l[3].toLong() }
//                .getOrElse { -22L }
//                .onNegative { err -> return err }
//            if (pgid == 1L) {
//                return runCatching { l[0].toLong() }
//                    .getOrElse { -23L }
//            } else {
//                return pgid
//            }
//        }
//        return -24L
//    }
//
//    fun killProcessByPgid(logger: Logger?, pgid: Long) : Int {
//        val p = ProcessUtil.startAndWait(
//			logger, "pkill", arrayOf("pkill", "-g", pgid.toString())
//		)
//            ?: return -11
//        val ev = p.exitValue()
//        if (ev != 0) {
//            LogUtil.info(logger, "pkill exits with {}", ev)
//            return -20 - ev
//        }
//
//        LogUtil.info(null, "pkill'ed pgid: {}", pgid)
//        return 1
//    }
//
//
//
}
