package com.brainzsquare.zf.config

import com.brainzsquare.zf.Global


object Consts {
    const val appName = "zf"
    val packagePrefix: String = "${Global::class.java.`package`.name}."

    // ck == config key

    val ckDevMode = "${packagePrefix}devMode"
    val ckNoSystemExitOnInitError = "${packagePrefix}noSystemExitOnInitError"
    val ckLogLevel = "${packagePrefix}logLevel"

    val ckAllowedIp = "${packagePrefix}allowedIp"

    // on linux, path to R
    // on windows, path to Rserve
    val ckRPath = "${packagePrefix}rPath"

    // optional
    // on windows, ckRMaxConnections consecutive ports from this port are used
    val ckRPort = "${packagePrefix}rPort"
    const val defaultRPort = 6311

    // max connections. default 3
    val ckRConnCount = "${packagePrefix}rConnCount"
    const val defaultRConnCount = 3

    ////////

    val rLibs = arrayOf("Rserve", "forecast", "prophet")
    const val lockTimeout = 10 * 1000L
}
