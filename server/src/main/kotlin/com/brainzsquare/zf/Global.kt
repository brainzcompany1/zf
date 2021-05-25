package com.brainzsquare.zf

import com.brainzsquare.bcommons.error.*
import com.brainzsquare.bcommons.log.*
import com.brainzsquare.zf.config.Consts
import com.brainzsquare.zf.r.RHandler2
import com.brainzsquare.zf.servlet.ForecastServlet
import com.brainzsquare.zf.servlet.PingServlet
import com.brainzsquare.bcommons.servlet.ServletUtil
import com.brainzsquare.zf.servlet.MyTestServlet
import org.slf4j.Logger
import java.lang.IllegalStateException
import java.util.regex.Pattern
import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.ServletContext
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.http.HttpServlet
import kotlin.system.exitProcess


class MyServletContextListener : ServletContextListener {

    // throw exception when init fails. cf. Servlet 2.4 spec
    override fun contextInitialized(sce: ServletContextEvent) {
        val sc = sce.servletContext
        val cname = sc.servletContextName ?: "<null context>"

        LogUtil.info(null, "\n\nMyServletContextListener.contextInitialized starts. context: $cname\n\n")
        val tt: Throwable? = try {
            Global.sc = sc
            if (Global.global == null) {
                IllegalStateException("init global failed")
            } else {
                null
            }
        } catch (t: Throwable) {
            IllegalStateException("unexpected exception while initializing global", t)
        }
        if (tt != null) {
            val noSystemExitOnInitError = ServletUtil.getBooleanOrDefault(
                MyErr(), sc, Consts.ckNoSystemExitOnInitError, false)
            if (! noSystemExitOnInitError) {
                LogUtil.info(null, "exiting...")
                exitProcess(1)
            }
            throw tt
        }

        Global.global?.logger?.info("\n\nLET'S GO!\n\n")
        LogUtil.info(null, "\n\nLET'S GO!\n\n")
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        val cname = sce.servletContext?.servletContextName ?: "<null context>"
        if (Global.global != null) {
            Global.global?.logger?.info("\n\nMyServletContextListener.contextDestroyed starts. context: $cname\n\n")
            Global.global?.shutDown()
            LogUtil.info(null, "\n\nMyServletContextListener.contextDestroyed done. context: $cname\n\n")
        } else {
            LogUtil.info(null, "\n\nMyServletContextListener.contextDestroyed done. context: $cname\n\n")
        }
    }
}


class Global @Throws(MyException::class) private constructor(sc: ServletContext) {
    companion object {
        var sc: ServletContext? = null
        val global: Global? by lazy {
            val _sc = sc!!
            runCatching {
                Global(_sc)
            }.onFailure {
                val err = MyErr()
                err.newErrUnexpected("init global failed. ", exception = it)
                err.log(null)
            }.getOrNull()
        }
    }

    /////////////////////////////////////////////////////////////////////////

    val logger: Logger
    val rh2: RHandler2
    val devMode: Boolean

    init {
        var err = MyErr()
        var ok = false
        var logger: Logger? = null
        var rh: RHandler2? = null
        try {
            LogUtil.info(logger, "setting up Global...")
            this.devMode = ServletUtil.getBooleanOrDefault(
                MyErr(), sc, Consts.ckDevMode, false)
            logger = setUpLogger(err, sc, this.devMode)
                ?: run {
                    err.addLocation(object{})
                    throw MyException(err)
                }
            // yeah! from this point on, we can use logger.
            logger.info("\n\n\nHELLO.\n\n")
            logger.info("devMode: ${this.devMode}")

            val rPath = ServletUtil.servletParamOrDefault(sc, Consts.ckRPath)
            if (rPath.isEmpty()) {
                err.newErr(ServletUtil.ecServletContextInitParam, "empty init param ${Consts.ckRPath}", locObj = object{})
                throw MyException(err)
            }

            val rPort = ServletUtil.getIntOrDefault(err, sc, Consts.ckRPort, Consts.defaultRPort)
            if (err.isError) {
                err.addLocation(object{})
                throw MyException(err)
            }
            if (rPort < 1 || rPort > 65535) {
                err.newErr(ServletUtil.ecServletContextInitParam, "invalid init param ${Consts.ckRPort}: $rPort",
                    locObj = object{})
                throw MyException(err)
            }

            val rConnCount = ServletUtil.getIntOrDefault(err, sc, Consts.ckRConnCount, Consts.defaultRConnCount)
            if (err.isError) {
                err.addLocation(object{})
                err.addMsg("for init param ${Consts.ckRConnCount}")
                throw MyException(err)
            }
            if (rConnCount < 1 || rConnCount > 99) {
                err.newErr(
                    ServletUtil.ecServletContextInitParam, "invalid init param ${Consts.ckRConnCount}: $rConnCount",
                    locObj = object{})
                throw MyException(err)
            }
            rh = RHandler2.create(err, logger,
                Consts.lockTimeout,
                rPath, rPort, rConnCount, Consts.rLibs,
                devMode
            )
                ?: run {
                    err.addLocation(object{})
                    throw MyException(err)
                }

            val servlets = HashMap<String, Class<out HttpServlet>>()
            run {
                servlets["/forecast"] = ForecastServlet::class.java
                servlets["/ping"] = PingServlet::class.java
                if (devMode) {
                    servlets["/test"] = MyTestServlet::class.java
                }
                for ((name, clazz) in servlets) {
                    sc.addServlet(name, clazz).addMapping(name)
                    logger.info("servlet {} registered.", name)
                }
            }

            // set up ip filter for servlets only Zenius web servers can access.
            // defaults to "127\\.0\\.0\\.1"
            run {
                val filterName = "Remote Address Filter"
                var allowed = ServletUtil.servletParamOrDefault(
                    sc, Consts.ckAllowedIp, "127\\.0\\.0\\.1")
                try {
                    // check validity of regex
                    Pattern.compile(allowed)
                } catch (t: Throwable) {
                    err.newErr(
                        ServletUtil.ecServletContextInitParam, "init param ${Consts.ckAllowedIp} is invalid Regex: $allowed",
                        exception = t)
                    throw MyException(err)
                }
                val reg = sc.addFilter(
                    filterName, "org.apache.catalina.filters.RemoteAddrFilter")
                reg.setInitParameter("allow", allowed)
                reg.addMappingForServletNames(null, false, *servlets.keys.toTypedArray())
                logger.info("{} configured for servlets. allow: {}", filterName, allowed)
            }

            this.logger = logger
            this.rh2 = rh
            ok = true
            logger.info("global initialized")
        } catch(m: MyException) {
            m.err.log(logger)
            LogUtil.error(logger, "init global failed.")
            throw m
        } catch (t: Throwable) {
            LogUtil.unexpected(
                logger,
                t,
                LogUtil.msgUnexpectedException)
            LogUtil.error(logger, "init global failed.")
            throw t
        } finally {
            if (! ok) {
                this.doShutDown(logger, rh)
            }
        }
    }

    fun shutDown(): Boolean {
        if (!this.shutDownState.compareAndSet(0, 1)) {
            return false
        }

        this.logger.info("\n\nSHUTDOWN SEQUENCE initiated.\n\n")
        this.doShutDown(this.logger, rh2)

        if (! this.shutDownState.compareAndSet(1, 2)) {
            LogUtil.unexpected(null)
        }
        return true
    }

    /////////////////////////////////////////////////////////////////////////

    private val shutDownState: AtomicInteger = AtomicInteger(0)

    private fun setUpLogger(err: MyErr, sc: ServletContext, devMode: Boolean): Logger? {
        val tomcatDir = (System.getProperty("catalina.base") ?: "")
            .run { if (this.endsWith("/")) this else "$this/" }
        val logToConsole = devMode
        val logLevel = run {
            val l = LogUtil.getLogLevelOrDefault(
                sc.getInitParameter(Consts.ckLogLevel), "DEBUG")
            when (devMode) {
                true -> when (l) {
                    "INFO", "WARN", "ERROR" -> "DEBUG"
                    else -> l
                }
                else -> l
            }
        }
        // initialize logger /////////////////////////////////////////////
        val loggerConfig = StringBuilder().let {
            it.append("<configuration>\n")
            if (logToConsole) {
                it.append("""
                    |  <appender name="console_appender" class="ch.qos.logback.core.ConsoleAppender">
                    |    <withJansi>true</withJansi>
                    |    <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
                    |      <charset>UTF-8</charset>
                    |      <pattern>%d{yyyyMMdd'T'HHmmss.SSSZ} %-4.-4logger %-10.10t %highlight(%-5level) %m%n
                    |      </pattern>
                    |    </encoder>
                    |  </appender>
                    """.trimMargin())
            }
            it.append("""
                    |  <appender name="file_appender" class="ch.qos.logback.core.rolling.RollingFileAppender">
                    |    <file>${tomcatDir}logs/${Consts.appName}.log</file>
                    |    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                    |      <fileNamePattern>${tomcatDir}logs/${Consts.appName}.%d{yyyyMMdd}.%i.log</fileNamePattern>
                    |      <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                    |        <maxFileSize>100MB</maxFileSize>
                    |      </timeBasedFileNamingAndTriggeringPolicy>
                    |      <maxHistory>30</maxHistory>
                    |      <totalSizeCap>3GB</totalSizeCap>
                    |    </rollingPolicy>
                    |    <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
                    |      <charset>UTF-8</charset>
                    |      <pattern>%d{yyyyMMdd'T'HHmmss.SSSZ} %-4.-4logger %-10.10t %highlight(%-5level) %m%n
                    |      </pattern>
                    |    </encoder>
                    |  </appender>
                    |  <root level="$logLevel">
                    |    <appender-ref ref="file_appender"/>
                    |    <appender-ref ref="console_appender"/>
                    |  </root>
                    |</configuration>
                    """.trimMargin())
        }.toString()
        return LogUtil.setupLogger(err, Consts.appName, loggerConfig)
            ?: run {
                err.addLocation(object{})
                return null
            }
    }

    private fun doShutDown(logger: Logger?, rh: RHandler2?) {
        if (rh != null) {
            rh.shutDown()
        }

        logger?.info("logger shutting down...")
        LogUtil.info(logger, "global shutdown.")
        LogUtil.info(logger, "\n\nGOOD-BYE.\n\n")
        if (logger != null) LogUtil.shutDownLogger(logger)
    }
}
