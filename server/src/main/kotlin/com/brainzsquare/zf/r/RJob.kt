package com.brainzsquare.zf.r

import com.brainzsquare.bcommons.log.LogUtil
import org.rosuda.REngine.REXP
import org.rosuda.REngine.REXPDouble
import org.rosuda.REngine.REXPString
import org.rosuda.REngine.REngineException
import org.rosuda.REngine.Rserve.RConnection
import org.rosuda.REngine.Rserve.RserveException
import org.slf4j.Logger


class RJob
    internal constructor(val cmds: List<RCommand>, val devMode: Boolean)
{
    fun execute(logger: Logger, rconn: RConnection): Int {
        val started = System.currentTimeMillis()
        var rc = 1
        try {
            val sb = StringBuilder()
            for (i in cmds.withIndex()) {
                val str = "<${i.index}>: ${i.value.fullCmdStr}"
                if (this.devMode) logger.debug(str)
                val r = i.value.execute(rconn, sb)
                result.add(r)
                if (r.err != null) {
                    logger.error("error at $str. err: {} msg: {}", r.err, r.err.msg)
                    rc = -1
                    break
                } else if (this.devMode) {
                    if (r.rexp is REXPDouble && r.rexp.length() == 1) {
                        logger.debug("result: REXPDouble ${r.rexp.asDouble()}")
                    } else if (r.rexp is REXPString && r.rexp.length() == 1) {
                        logger.debug("result: REXPString ${r.rexp.asString()}")
                    } else {
                        logger.debug("result: ${r.rexp}")
                    }
                }
            }
            return rc
        } finally {
            LogUtil.debug(logger, "executed {} cmd(s). elapsed: {} msec",
                result.size, System.currentTimeMillis()-started)
            clearVars(logger, rconn)
        }
    }

    fun lastResult(): RResult
        = if (result.size == 0) {
            RResult.unexpected(null, "no result")
        } else {
            result.last()
        }

    val result = ArrayList<RResult>()

    ////////

    private fun clearVars(logger: Logger?, rconn: RConnection): Int {
        if (varsToRemove.size == 0) return 1

        val sb = StringBuilder()
        sb.append("remove(")
            .append(varsToRemove.joinToString(","))
            .append(")")
        RResult.evalAndGet(rconn, sb.toString())
            .onFailure {
                LogUtil.info(logger, "error while removing vars. {}", it.msg)
                return -1
            }

        LogUtil.debug(logger, "cleared {} vars", varsToRemove.size)
        return 1
    }

    private val varsToRemove = HashSet<String>()
    init {
        cmds.forEach {
            if (it.v != null) {
                varsToRemove.add(it.v)
            }
        }
    }
}

class RJobBuilder(val devMode: Boolean) {
    fun add(cmdStr: String, v: String? = null, retrieve: Boolean = false): RJobBuilder {
        cmds.add(RCommandByString(cmdStr, v, retrieve))
        return this
    }

    fun add(rexp: REXP, v: String): RJobBuilder {
        cmds.add(RCommandAssignREXP(rexp, v))
        return this
    }

    fun build() = RJob(cmds, devMode)

    private val cmds = ArrayList<RCommand>()
}

sealed class RCommand(val cmdStr: String, val v: String?, val retrieve: Boolean) {
    abstract fun execute(rconn: RConnection, sb0: StringBuilder?): RResult

    val fullCmdStr: String
        get() {
            return if (this.v == null) this.cmdStr else "${this.v} <- ${this.cmdStr}"
        }
}

class RCommandByString(cmdStr: String, v: String?, retrieve: Boolean): RCommand(cmdStr, v, retrieve) {
    override fun execute(rconn: RConnection, sb0: StringBuilder?): RResult
        = RResult.evalAndGet(rconn, cmdByString(sb0))

    private fun cmdByString(sb0: StringBuilder?): String {
        val sb = sb0 ?: StringBuilder()
        // 왜 retrieve 일때만???
        //if (retrieve) sb.append("try( ")
        sb.append("try( ")
        if (v != null) {
            sb.append(v).append(" <- ")
        }
        sb.append(cmdStr)
        //if (retrieve) sb.append(" , silent=TRUE)")
        sb.append(" , silent=TRUE)")
        val s = sb.toString()
        sb.clear()
        return s
    }
}

class RCommandAssignREXP(val rexp: REXP, v: String): RCommand("<${rexp.javaClass.simpleName}>", v, false) {
    override fun execute(rconn: RConnection, sb0: StringBuilder?): RResult {
        try {
            rconn.assign(v, rexp, null)
        } catch (rex: REngineException) {
            return RResult.rException(rex)
        }
        return RResult.ok
    }
}
