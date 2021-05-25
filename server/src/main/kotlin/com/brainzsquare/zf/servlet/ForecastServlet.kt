package com.brainzsquare.zf.servlet

import com.brainzsquare.bcommons.io.IOUtil
import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.error.onNegative
import com.brainzsquare.zf.forecast.Forecast
import com.brainzsquare.zf.forecast.ForecastResult
import com.brainzsquare.zf.forecast.ForecastMethod
import com.brainzsquare.zf.r.RHandler2
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class ForecastServlet: MyServletBase() {
    override fun doUnexpectedError(hreq: HttpServletRequest, hres: HttpServletResponse, exception: Throwable?) {
        this.sendResult(hreq, hres, ForecastResult.unexpected(exception = exception))
    }

    override fun init() {
        super.init()
        logger.info("servlet: ${this.servletName} initialized.")
    }

    override fun destroy() {
        super.destroy()
        executor.shutdownNow()
        logger.info("executor destroyed.")
        logger.info("servlet: ${this.servletName} destroyed.")
    }

    override fun doPost(hreq: HttpServletRequest, hres: HttpServletResponse) {
        this.wrapDo(hreq, hres) { this.doProcess(hreq, hres) }
    }

    ////////

    companion object {
        fun doForecast(err: MyErr, inputStr: String): ForecastResult {
            val p = runCatching {
                mapper.readValue<ForecastInput>(inputStr)
            }.onFailure {
                logger.info("invalid json. ${it.toString()}")
                return ForecastResult.inputError(exception = it)
            }.getOrNull()!!

            // validate params

            if (p.method == ForecastMethod.PROPHET && p.startDt == null) {
                return ForecastResult.inputError(msg = "method PROPHET requires startDt")
            }
            val startDt = Calendar.getInstance()
            if (p.startDt != null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                val dt = try {
                    sdf.parse(p.startDt)
                } catch (e: ParseException) {
                    return ForecastResult.inputError(msg = "invalid startDt: ${p.startDt}")
                }
                val cal = Calendar.getInstance()
                cal.time = dt
            }

            val data = DoubleArray(p.data.size)
            this.dataInDouble(err, p.data, data)
                .onNegative {
                    err.addLocation(object{})
                    return ForecastResult.inputError(msg = "invalid data. ${err.lastErr?.msg}")
                }
            val missingCount = data.count { it.isNaN() }
            if (missingCount >= data.size/2) {
                return ForecastResult.inputError(msg = "invalid data. too many missing data: $missingCount outof total ${data.size}")
            }

            val myR = rh2.getR(err)
                ?: run {
                    if (err.errCode == RHandler2.ecAllMyRBusy.errCode) {
                        return ForecastResult.busy("rConnCount: ${rh2.rConnCount}")
                    } else {
                        val sb = StringBuilder()
                        sb.append("cannot get myR. ")
                        err.log(sb)
                        return ForecastResult.unexpected(sb.toString())
                    }
                }

            if (devMode) {
                logger.debug("forecast input: {}", inputStr)
            }
            var needNewMyR = false
            try {
                val startedMsec = System.currentTimeMillis()
                val threadName = Thread.currentThread().name
                val future = executor.submit(Callable<ForecastResult> {
                    Forecast.forecast(
                        logger, myR,
                        p.infoStr,
                        p.method, data, p.frequency, startDt, p.canBeNegative,
                        p.hrz, p.pi,
                        devMode = devMode,
                        threadName = threadName
                    )
                })
                val result = future.get(p.jobTimeout, TimeUnit.MILLISECONDS)
                    ?: return ForecastResult.unexpected("location 1")
                if (! result.errCode.isEmpty()) {
                    needNewMyR = true
                } else {
                    logger.info("calculation took {} msec", System.currentTimeMillis()-startedMsec)
                }
                return result
            } catch (e: TimeoutException) {
                needNewMyR = true
                return ForecastResult.jobTimeout(p.jobTimeout)
            } finally {
                val err1 = MyErr()
                rh2.returnR(err1, myR, needNewMyR)
                    .onNegative {
                        err1.addLocation(object{})
                        err1.log(logger)
                        err1.errCode
                    }
            }
        }

        private val ecInvalidInputParam = MyErr.register("ecInvalidForecastInputParam")

        private const val defaultJobTimeout = 3*60*1000L

        private val executor = Executors.newFixedThreadPool(rh2.rConnCount) {
            val th = Thread(it)
            th.name = "rw-${th.id}"
            th
        }

        private fun dataInDouble(err: MyErr, strArr: Array<Any?>, result: DoubleArray): Int {
            for (i in result.indices) {
                result[i] = when (val a = strArr[i]) {
                    null -> Double.NaN
                    is String -> {
                        if (a.isEmpty() || a == "NA")
                            Double.NaN
                        else try {
                            a.toDouble()
                        } catch (e: NumberFormatException) {
                            err.newErr(ecInvalidInputParam, "invalid number, at index $i, value $a")
                            return err.errCode
                        }
                    }
                    is Number -> {
                        a.toDouble()
                    }
                    else -> {
                        err.newErr(ecInvalidInputParam, "invalid number, at index $i, value $a")
                        return err.errCode
                    }
                }
            }
            return 1
        }
    }

    private class ForecastInput(
        // for info, debugging, ...
        val infoStr: String?,

        // can contail null or "NA". So instead of Double, its type is Any?
        val data: Array<Any?>,
        // forecast horizon
        val hrz: Int,

        val method: ForecastMethod = ForecastMethod.NN,
        // 하루에 data point가 몇개 있는가? (첨에 이렇게 시작해서 이제 못 바꿈)
        val frequency: Int = 24,
        // 20200900 추가. method가 PROPHET인 경우만 필수.
        // 형식은 yyyy-MM-dd HH:mm:ss
        val startDt: String?,

        // 20200800 추가
        val pi: Boolean = true,
        val canBeNegative: Boolean = false,

        val jobTimeout: Long = defaultJobTimeout,
    )

    private fun doProcess(hreq: HttpServletRequest, hres: HttpServletResponse) {
        val err = MyErr()
        val inputStr = IOUtil.readAllAsUTF8String(err, hreq.inputStream)
            ?: run {
                logger.info("IOException while processing request from {}", hreq.remoteAddr)
                return
            }
        this.sendResult(hreq, hres, doForecast(err, inputStr))
    }

    private fun sendResult(hreq: HttpServletRequest, hres: HttpServletResponse, result: ForecastResult) {
        hres.contentType = "application/json"
        hres.characterEncoding = "UTF-8"
        if (result.errCode.isNotEmpty()) {
            logger.info("error result: {}", mapper.writeValueAsString(result))
        } else if (devMode) {
            logger.debug("forecast result: {}", mapper.writeValueAsString(result))
        }
        try {
            mapper.writeValue(hres.writer, result)
        } catch (e: IOException) {
            logger.info("IOException while sending result to {}", hreq.remoteAddr)
        }
    }
}
