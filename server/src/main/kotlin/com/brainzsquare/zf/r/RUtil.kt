package com.brainzsquare.zf.r

import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.error.onNegative
import com.brainzsquare.bcommons.misc.TimeUtil
import com.brainzsquare.bcommons.os.LinuxUtil
import com.brainzsquare.bcommons.os.ProcessUtil
import com.brainzsquare.bcommons.text.toLong
import org.rosuda.REngine.REXP
import org.rosuda.REngine.REXPMismatchException
import org.rosuda.REngine.REngineException
import org.rosuda.REngine.Rserve.RConnection


object RUtil {
    val ecRError = MyErr.register("ecRError")
    val ecREngineException = MyErr.register("REngineException")

    const val rServeName = "Rserve"
    const val waitTime = 500L
    const val waitCount = 10
    const val rServeInitWaitTime = waitTime * 5

    fun killAllRserveOnLinux(err: MyErr): List<Long> {
        val result = ArrayList<Long>()
        val s = ProcessUtil.runAndCapture(err, arrayOf("pidof", rServeName))
            ?: run {
                err.addLocation(object {})
                return result
            }
        if (s.isEmpty()) return result
        val list = s.trim().split(" ")
        for (pidStr in list) {
            val pid = pidStr.toLong(err)
                .onNegative {
                    err.addLocation(object {})
                    err.errCode.toLong()
                }
            if (pid > 0) {
                LinuxUtil.killProcess(err, pid)
                result.add(pid)
            }
        }
        return result
    }

    fun startRserveOnLinux(err: MyErr, rPath: String, port: Int): Long {
        ProcessUtil.startAndWait(err, arrayOf(
            rPath, "CMD", rServeName,
            "--vanilla", "--slave",
            "--RS-port port", port.toString(),
            "--RS-enable-control"
            ))
            ?: run {
                err.addLocation(object {})
                return err.errCode.toLong()
            }

        for (i in 0 until waitCount) {
            val s = ProcessUtil.runAndCapture(err, arrayOf("pidof", rServeName))
                ?: run {
                    err.addLocation(object {})
                    return err.errCode.toLong()
                }
            val list = s.trim().split(" ")
            if (list.isEmpty()) {
                err.newErrGeneral("unexpected output of pidof: $s")
                return err.errCode.toLong()
            }
            if (list[0].isNotEmpty()) {
                return list[0].toLong(err)
                    .onNegative {
                        err.addLocation(object {})
                        return err.errCode.toLong()
                    }
            }
            if (i != waitCount - 1) {
                TimeUtil.sleep(err, waitTime)
                    .onNegative {
                        err.addLocation(object{})
                        return err.errCode.toLong()
                    }
            }
        }
        err.newErrUnexpected("cannot find pidof $rPath")
        return err.errCode.toLong()
    }

    /**
     * @return
     * 1 shutDown and OK
     * 2 kill and OK
     * 3 kill -9 and OK
     */
    fun destroyOnLinux(err: MyErr, rconn: RConnection, pid: Long, tryShutDown: Boolean): Int {
        if (tryShutDown) {
            var shutDownOk = false
            try {
                rconn.shutdown()
                shutDownOk = true
            } catch (t: Throwable) {
                err.newErrGeneral(exception = t)
            }
            if (shutDownOk) {
                val rc = LinuxUtil.waitProcessDone(err, pid)
                    .onNegative {
                        err.addLocation(object {})
                        return err.errCode
                    }
                if (rc == 0) return 1
                // otherwise flow though down
            }
            // otherwise flow though down
        }

        val rc = LinuxUtil.killProcess(err, pid)
            .onNegative {
                err.addLocation(object{})
                return err.errCode
            }
        return rc + 1
    }

    fun startRserveOnWindows(err: MyErr, rServePath: String, port: Int): Process? {
        var process: Process? = null
        try {
            process = ProcessUtil.start(
                err, arrayOf(
                    rServePath,
                    "--vanilla", "--slave",
                    "--RS-port", port.toString(),
                    "--RS-enable-control"
                ))
                ?: run {
                    err.addLocation(object {})
                    return null
                }
            // wait a little bit for the r process to stabilizes
            TimeUtil.sleep(err, rServeInitWaitTime)
                .onNegative {
                    err.addLocation(object{})
                    return null
                }
            return process
        } finally {
            if (err.isError) {
                if (process != null) {
                    ProcessUtil.destroy(err, process)
                }
            }
        }
    }

    fun destroyOnWindows(err: MyErr, rconn: RConnection, process: Process, tryShutDown: Boolean): Int {
        if (tryShutDown) {
            var shutDownOk = false
            try {
                rconn.serverShutdown()
                shutDownOk = true
            } catch (t: Throwable) {
                err.newErrGeneral(exception = t)
            }
            if (shutDownOk) {
                val rc = ProcessUtil.waitEndOrDestroy(err, process)
                    .onNegative {
                        err.addLocation(object {})
                        return err.errCode
                    }
                return rc
            }
            // otherwise flow though down
        }

        return ProcessUtil.destroy(err, process)
            .onNegative {
                err.addLocation(object {})
                err.errCode
            }
    }

    fun executeAndRetrieve(err: MyErr, rconn: RConnection, cmdStr: String): REXP? {
        try {
            val rexp = rconn.eval("try($cmdStr, silent=TRUE)")
            if (rexp.inherits("try-error")) {
                err.newErr(ecRError, "error from R. cmd: $cmdStr. error: $rexp")
                return null
            }
            return rexp
        } catch (e: REngineException) {
            err.newErr(ecREngineException, exception = e)
            return null
        }
    }

    fun getLong(err: MyErr, rexp: REXP, errRv: Long = Long.MIN_VALUE): Long {
        return try {
            rexp.asDouble().toLong()
        } catch (e: REXPMismatchException) {
            err.newErrGeneral("rexp: $rexp", exception = e)
            errRv
        }
    }

    fun getInt(err: MyErr, rexp: REXP, errRv: Int = Int.MIN_VALUE): Int {
        return try {
            rexp.asInteger()
        } catch (e: REXPMismatchException) {
            err.newErrGeneral("rexp: $rexp", exception = e)
            errRv
        }
    }

    fun doubleVector(r: REXP): DoubleArray? {
        return try {
            r.asDoubles()
        } catch(t: Throwable) {
            null
        }
    }
}
