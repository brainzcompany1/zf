package com.brainzsquare.bcommons.os

import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.error.onNegative
import com.brainzsquare.bcommons.text.toLong


object WindowsUtil {
    private val spaceRegex = Regex("\\s+")

    fun findPidByPort(err: MyErr, port: Int): Long {
        val args = arrayOf("netstat", "-n", "-o", "-p", "TCP")
        val output = ProcessUtil.runAndCapture(err, args) ?: run {
            err.addLocation(object{})
            return -1L
        }
        val first = run {
            var first = -1
            for (i in 0 until output.length - 1) {
                if (output[i] == '\r' && output[i+1] == '\n') {
                    if (i+2 < output.length) first = i+2
                    break
                }
            }
            first
        }.onNegative {
            return 0
        }
        val lines = output.substring(first).split("\r\n")
        val local = "127.0.0.1:$port"
        for (line in lines) {
            val words = line.trim().split(spaceRegex)
            if (words.size < 2) continue
            if (words[1] == local) {
                if (words.size < 5) {
                    err.newErrUnexpected("words.size < 5. output of netstat. line: $line", locObj = object{})
                    return err.errCode.toLong()
                }
                return words[4].toLong(err).onNegative {
                    if (err.isError) {
                        err.newErrUnexpected("words[4].toLong failed. output of netstat. line: $line")
                    }
                    err.addLocation(object{})
                    return err.errCode.toLong()
                }
            }
        }
        return 0
    }

    /**
     * @return  @code{1}    OK
     *          @code{2}    OK. killed forcefully
     *          negative    error occurred
     */
    fun killProcessByPid(err: MyErr, pid: Long) : Int {
        val p = ProcessUtil.startAndWait(err, arrayOf("taskkill.exe", "/pid", "$pid"))
            ?: run {
                err.addLocation(object {})
                return err.errCode
            }
        if (p.exitValue() == 0) return 1
        // try /F option
        val p2 = ProcessUtil.startAndWait(err, arrayOf("taskkill.exe", "/pid", "$pid", "/F"))
            ?: run {
                err.addLocation(object {})
                return err.errCode
            }
        val rc = p2.exitValue()
        if (rc == 0) return 2
        else if (rc == 128) return 3

        err.newErrUnexpected("cannot kill $pid")
        return err.errCode
    }

//    // 0 cannot find
//    // -1 .. -6
//    // name must not contain spaces
//    fun findProcessByName(logger: Logger?, name: String) : Long {
//        val p = ProcessUtil.startAndWait(
//			logger, "tasklist.exe /fo CSV /nh /fi \"IMAGENAME eq $name\""
//		)
//            ?: return -11L
//        p.inputStream.use {
//            val s = runCatching {
//				IOUtil.readAllAsUTF8String(logger, it)
//            }.onFailure {
//                LogUtil.unexpected(logger, it)
//            }.getOrNull() ?: return -1L
//            val list = MiscUtil.splitCsvFirstLine(logger, s)
//                ?: return -2L
//            if (list.size < 2) {
//                val ss = list[0].toLowerCase(Locale.ENGLISH)
//                if (! ss.startsWith("info:")) {
//                    LogUtil.unexpected(logger, "s: {}", s)
//                    return -3L
//                }
//                return 0
//            }
//            if (list[0] != name) {
//                LogUtil.unexpected(logger, "s: {}", s)
//                return -4L
//            }
//            return runCatching { list[1].toLong() }
//                .getOrElse { -5L }
//        }
//    }
}
