package com.brainzsquare.zf.forecast

import com.brainzsquare.zf.r.*
import org.rosuda.REngine.REXPDouble
import org.rosuda.REngine.REXPString
import org.slf4j.Logger
import java.text.SimpleDateFormat
import java.util.Calendar


enum class ForecastMethod {
    ARIMA, // ARIMA ARIMA. auto.arima. for compatibility only.
    ETS, // ETS 지수평활법.
    NN, // NN 인공신경망.
    PROPHET, // PROPHET PROPHET.
    STL, // STL 계절성-트렌드 분해.
    HR, // Harmonic Regression 조화회귀분석.
    ;
}

object Forecast {
    private const val nnPISimulationCount = 500

    /**
     * @return @code{ForecastResult}
     */
    fun forecast(
        logger: Logger, myR: MyR,
        infoStr: String?,
        method: ForecastMethod,
        data: DoubleArray, frequency: Int, startDt: Calendar, canBeNegative: Boolean,
        hrz: Int, pi: Boolean,
        devMode: Boolean = false,
        threadName: String? = null
        ): ForecastResult
    {
        if (! threadName.isNullOrEmpty()) {
            logger.debug("from thread: {}", threadName)
        }
        val rJob = when (method) {
            ForecastMethod.ARIMA ->
                arimaRJob(
                    data, frequency, canBeNegative,
                    hrz, pi,
                    devMode
                )
            ForecastMethod.ETS ->
                etsRJob(
                    data, frequency, canBeNegative,
                    hrz, pi,
                    devMode
                )
            ForecastMethod.NN ->
                nnetarRJob(
                    data, frequency, canBeNegative,
                    hrz, pi,
                    devMode
                )
            ForecastMethod.PROPHET ->
                prophetRJob(
                    data, frequency, canBeNegative,
                    startDt,
                    hrz, pi,
                    devMode
                )
            ForecastMethod.STL ->
                stlRJob(
                    data, frequency, canBeNegative,
                    hrz, pi,
                    devMode
                )
            ForecastMethod.HR ->
                harmonicRegressionRJob(
                    data, frequency, canBeNegative,
                    hrz, pi,
                    devMode
                )
        }
        val infoStrToLog = if (infoStr == null) ""  else "infoStr: $infoStr "
        logger.debug(
            "starting forecast... $infoStrToLog method: {}, size: {}, frequency: {} hrz: {}, pi: {}, canBeNegative: {}",
            method, data.size, frequency, hrz, pi, canBeNegative
        )
        logger.debug("with myR: ${myR.pid}")
        rJob.execute(logger, myR.rconn)
        logger.debug("...done forecast $infoStrToLog")
        return ForecastResult.create(rJob.lastResult())
    }

    ////////

    // for compatibility only
    private fun arimaRJob(
        data: DoubleArray, frequency: Int, canBeNegative: Boolean,
        hrz: Int, pi: Boolean,
        devMode: Boolean
    ): RJob {
        val b = RJobBuilder(devMode)

        b.add("list()", "r")

        b.add(REXPDouble(data), "org")
        b.add("tsclean( ts(org, frequency=${frequency}) )", "tr")
        //b.add("na.interp( ts(org, frequency=${frequency}) )", "tr")

        b.add("r[[\"model\"]] <- auto.arima(tr)")
        val piArgs = if (pi) ", level=c(80,95), PI=TRUE" else ""
        b.add("forecast(r[[\"model\"]], h=${hrz}$piArgs)", "f")

        this.commonGetResult(b, pi)
        this.makePositive(b, pi, canBeNegative)
        b.add("r", retrieve = true)

        return b.build()
    }

    private fun etsRJob(
        data: DoubleArray, frequency: Int, canBeNegative: Boolean,
        hrz: Int, pi: Boolean,
        devMode: Boolean
    ): RJob {
        val b = RJobBuilder(devMode)

        b.add("list()", "r")

        b.add(REXPDouble(data), "org")
        b.add("tsclean( ts(org, frequency=${frequency}) )", "tr")
        //b.add("na.interp( ts(org, frequency=${frequency}) )", "tr")

        b.add("r[[\"model\"]] <- ets(tr)")
        val piArgs = if (pi) ", level=c(80,95), PI=TRUE" else ""
        b.add("forecast(r[[\"model\"]], h=${hrz}$piArgs)", "f")

        this.commonGetResult(b, pi)
        this.makePositive(b, pi, canBeNegative)
        b.add("r", retrieve = true)

        return b.build()
    }

    private fun nnetarRJob(
        data: DoubleArray, frequency: Int, canBeNegative: Boolean,
        hrz: Int, pi: Boolean,
        devMode: Boolean
    ): RJob {
        val b = RJobBuilder(devMode)

        b.add("list()", "r")

        b.add(REXPDouble(data), "org")
        b.add("tsclean( ts(org, frequency=${frequency}) )", "tr")
        //b.add("na.interp( ts(org, frequency=${frequency}) )", "tr")

        b.add("r[[\"model\"]] <- nnetar(tr)")
        val piArgs = if (pi) ", level=c(80,95), PI=TRUE" else ""
        b.add("forecast(r[[\"model\"]], h=${hrz}$piArgs)", "f")

        this.commonGetResult(b, pi)
        this.makePositive(b, pi, canBeNegative)
        b.add("r", retrieve = true)

        return b.build()
    }

    private fun stlRJob(
        data: DoubleArray, frequency: Int, canBeNegative: Boolean,
        hrz: Int, pi: Boolean,
        devMode: Boolean
    ): RJob {
        val b = RJobBuilder(devMode)

        b.add("list()", "r")

        b.add(REXPDouble(data), "org")
        if (data.size > frequency*14) {
            b.add("msts( tsclean( ts(org, frequency=$frequency) ), seasonal.periods=c($frequency, ${frequency*7}) )", "tr")
        } else if (data.size > frequency*2) {
            b.add("tsclean( ts(org, frequency=$frequency) )", "tr")
        }

        // 20201103 error "No model able to be fitted" occurs if PI=TRUE is added
        // don't know why.
        val piArgs = if (pi) ", level=c(80,95)" else ""
        b.add("stlf(tr, h=${hrz}, method=\"ets\"$piArgs)", "f")
        b.add("r[[\"model\"]] <- f\$model")

        this.commonGetResult(b, pi)
        this.makePositive(b, pi, canBeNegative)
        b.add("r", retrieve = true)

        return b.build()
    }

    private fun harmonicRegressionRJob(
        data: DoubleArray, frequency: Int, canBeNegative: Boolean,
        hrz: Int, pi: Boolean,
        devMode: Boolean
    ): RJob {
        // TODO what to do in this case?
        if (data.size <= frequency*7) {
            return etsRJob(data, frequency, canBeNegative, hrz, pi, devMode)
        }

        val b = RJobBuilder(devMode)

        b.add("list()", "r")

        b.add(REXPDouble(data), "org")
        b.add("msts( tsclean( ts(org, frequency=$frequency) ), seasonal.periods=c($frequency, ${frequency*7}) )", "tr")

        val xreg = "xreg=fourier(tr, K=c(10,10))"
        b.add("r[[\"model\"]] <- auto.arima(tr, seasonal=FALSE, $xreg)")
        val piArgs = if (pi) ", level=c(80,95)" else ""
        b.add("forecast(r[[\"model\"]], $xreg, h=$hrz$piArgs)", "f")

        this.commonGetResult(b, pi)
        this.makePositive(b, pi, canBeNegative)
        b.add("r", retrieve = true)

        return b.build()
    }

    private fun commonGetResult(b: RJobBuilder, pi: Boolean) {
        b.add("r[[\"forecast\"]] <- as.numeric(f\$mean)")
        if (pi) {
            b.add("r[[\"lower1\"]] <- as.numeric(f\$lower[,1])")
            b.add("r[[\"upper1\"]] <- as.numeric(f\$upper[,1])")
            b.add("r[[\"lower2\"]] <- as.numeric(f\$lower[,2])")
            b.add("r[[\"upper2\"]] <- as.numeric(f\$upper[,2])")
        }
    }

    private fun makePositive(b: RJobBuilder, pi: Boolean, canBeNegative: Boolean) {
		if (canBeNegative) return

        b.add("r[[\"forecast\"]] <- sapply(r[[\"forecast\"]], max, 0)")
        if (pi) {
            b.add("r[[\"lower1\"]] <- sapply(r[[\"lower1\"]], max, 0)")
            b.add("r[[\"upper1\"]] <- sapply(r[[\"upper1\"]], max, 0)")
            b.add("r[[\"lower2\"]] <- sapply(r[[\"lower2\"]], max, 0)")
            b.add("r[[\"upper2\"]] <- sapply(r[[\"upper2\"]], max, 0)")
        }
    }

    private fun prophetRJob(
        data: DoubleArray, frequency: Int, canBeNegative: Boolean,
        startDt: Calendar,
        hrz: Int, pi: Boolean,
        devMode: Boolean
    ): RJob {
        val intervalInSec = 60*60*24/frequency
        val sb = StringBuilder()
        val b = RJobBuilder(devMode)

        b.add("list()", "r")
        b.add(REXPDouble(data), "org")
        b.add("tsclean( ts(org, frequency=${frequency}) )", "tr")
        //b.add("na.interp( ts(org, frequency=${frequency}) )", "tr")
        val ds = run {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val s = Array<String>(data.size) {
                val dt = startDt.time
                startDt.add(Calendar.SECOND, intervalInSec)
                sdf.format(dt)
            }
            REXPString(s)
        }
        b.add(ds, "ds")
        b.add("data.frame(\"ds\"=ds, \"y\"=org)", "df")

        if (pi) {
            prophetPredict(b, sb, hrz, intervalInSec, 0.8f, "1")
            prophetPredict(b, sb, hrz, intervalInSec, 0.95f, "2")
        } else {
            prophetPredict(b, sb, hrz, intervalInSec, 0.0f, "")
        }
        this.makePositive(b, pi, canBeNegative)
        b.add("r", retrieve = true)

        return b.build()
    }

    private fun prophetPredict(
        b: RJobBuilder, sb: StringBuilder,
        hrz: Int,
        intervalInSec: Int, piLevel: Float, piSuffix: String
    ) {
        sb.append("prophet(df")
        if (piLevel > 0.0f) {
            sb.append(", interval.width=$piLevel")
        }
        sb.append(")")
        b.add(sb.toString(), "model")
        sb.clear()

        sb.append("make_future_dataframe(model, include_history = FALSE")
            .append(", periods = ").append(hrz)
            .append(", freq = ").append(intervalInSec)
            .append(")")
        b.add(sb.toString(), "future")
        sb.clear()

        b.add("predict(model, future)", "result")

        b.add("r[[\"model\"]] <- model")
        b.add("r[[\"forecast\"]] <- result\$yhat")
        if (piLevel > 0.0f) {
            b.add("r[[\"lower$piSuffix\"]] <- result\$yhat_lower")
            b.add("r[[\"upper$piSuffix\"]] <- result\$yhat_upper")
        }
    }
}
