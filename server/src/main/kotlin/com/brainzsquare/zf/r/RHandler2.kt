package com.brainzsquare.zf.r

import com.brainzsquare.bcommons.os.ProcessUtil
import com.brainzsquare.bcommons.concurrent.ConcurrencyUtil
import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.error.MyException
import com.brainzsquare.bcommons.error.onNegative
import com.brainzsquare.bcommons.os.LinuxUtil
import com.brainzsquare.bcommons.os.WindowsUtil
import com.brainzsquare.zf.config.Consts
import com.brainzsquare.zf.servlet.MyServletBase
import org.slf4j.Logger
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayDeque
import kotlin.collections.HashSet


abstract class RHandler2
	protected constructor(
		val logger: Logger,
		val rConnCount: Int,
		val libs: Array<String>,
		val waitForConnection: Long,
		val devMode: Boolean
	)
{
	abstract fun shutDown(): Int

	companion object {
		val ecAllMyRBusy = MyErr.register("ecAllMyRBusy")

		fun create(err: MyErr, logger: Logger,
			waitForConnection: Long,
			rPath: String, rPort: Int, rConnCount: Int, rLibs: Array<String>,
			devMode: Boolean
		) : RHandler2? {
            val os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH)
			return try {
				when {
					os.indexOf("nux") > -1 -> {
						OnLinux(
							logger,
							waitForConnection,
							rPath, rPort, rConnCount,
							rLibs,
							devMode
						)
					}
					os.startsWith("windows") -> {
						val rPorts = IntArray(rConnCount) { it + rPort }
						OnWindows(
							logger,
							waitForConnection,
							rPath, rPorts,
							rLibs,
							devMode
						)
					}
					else -> {
						err.newErrUnexpected("unsupported os: $os")
						null
					}
				}
			} catch (m: MyException) {
				err.copyFrom(m.err)
				null
			}
		}
	}

	fun getR(err: MyErr): MyR? {
		ConcurrencyUtil.lock(err, this.lock, Consts.lockTimeout).onNegative { return null }
		try {
			run {
				val myR = this.availableMyR.removeFirstOrNull()
				if (myR != null) return myR
			}
			ConcurrencyUtil.await(err, this.cond, this.waitForConnection).onNegative {
				err.addLocation(object {})
				return null
			}
			val myR = this.availableMyR.removeFirstOrNull()
				?: run {
					err.newErr(ecAllMyRBusy, "cannot get MyR after ${this.waitForConnection} msec",
						locObj = object {})
					return null
				}
			if (MyServletBase.devMode) {
				MyServletBase.logger.debug("R obtained. ${myR.pid}")
			}
			return myR
		} finally {
			ConcurrencyUtil.unlock(err, this.lock).onNegative {
				// should not happen and cannot correctly recover
				err.addLocation(object {})
				err.errCode
			}
		}
	}

	fun returnR(err: MyErr, _myR: MyR, _needNew: Boolean): Int {
		val needNew = _needNew || ! _myR.rconn.isConnected
		if (needNew) {
			var err2 = MyErr()
			_myR.destroy(err2, false)
			if (err2.isError) {
				this.logger.info("error while destroying myR: {}", _myR.pid)
				err2.log(this.logger)
			} else {
				this.logger.info("myR destroyed: {}", _myR.pid)
			}
		}
		var ok = false
		var newMyR: MyR? = null
		try {
			val myR = if (needNew) {
				newMyR = this.newMyR(err, _myR.port)
					?: run {
						err.addLocation(object {})
						return err.errCode
					}
				newMyR
			} else {
				_myR
			}
			ConcurrencyUtil.lock(err, this.lock, Consts.lockTimeout).onNegative {
				err.addLocation(object {})
				return err.errCode
			}
			this.availableMyR.add(myR)
			if (newMyR != null) {
				this.allMyR.remove(_myR)
				this.allMyR.add(newMyR)
			}
			ConcurrencyUtil.signalAll(err, this.cond).onNegative {
				err.addLocation(object {})
				return err.errCode
			}
			ConcurrencyUtil.unlock(err, this.lock).onNegative {
				err.addLocation(object {})
				return err.errCode
			}
			ok = true
		} finally {
			if (! ok) {
				newMyR?.destroy(err, false)
			}
		}
		if (newMyR != null) {
			if (devMode) {
				logger.info("newR created: ${newMyR.pid}")
			}
			return 0
		} else {
			if (devMode) {
				logger.info("R returned OK: ${_myR.pid}")
			}
			return 1
		}
	}

	class OnLinux
		constructor(
			logger: Logger,
			waitForConnection: Long,
			val rPath: String, val port: Int, rConnCount: Int,
			libs: Array<String>,
			devMode: Boolean
		) : RHandler2(logger, rConnCount, libs, waitForConnection, devMode)
	{
		override fun shutDown(): Int {
			val err = MyErr()
			logger.info("destroying ${this.javaClass.name}...")
			ConcurrencyUtil.lock(err, this.lock, Consts.lockTimeout)
				.onNegative {
					err.addLocation(object{}).errCode
				}
			for (myR in this.allMyR) {
				val rc = myR.destroy(err, false)
					.onNegative {
						err.addLocation(object{}).errCode
					}
				logger.info("process: ${myR.pid} destroyed. rc: $rc")
			}
			val rc = LinuxUtil.killProcess(err, this.mainPid)
				.onNegative {
					err.addLocation(object{}).errCode
				}
			if (rc > 0) {
				logger.info("main process: ${this.mainPid} destroyed. rc: $rc")
			}

			this.allMyR.clear()
			this.availableMyR.clear()
			ConcurrencyUtil.unlock(err, this.lock)
				.onNegative {
					err.addLocation(object{}).errCode
				}

			if (err.isError) {
				logger.error("error while destroying ${this.javaClass.name}")
				err.log(logger)
				return err.errCode
			} else {
				logger.info("${this.javaClass.name} destroyed")
				return 1
			}
		}

		override fun newMyR(err: MyErr, port: Int): MyR? {
			return MyR.createOnLinux(err, port, this.libs)
				?: run {
					err.addLocation(object{})
					return null
				}
		}

		val mainPid: Long

		init {
			val err = MyErr()
			var mainPid = -1L
			try {
				logger.info("initializing ${this.javaClass.name}...")

				run {
					val list = RUtil.killAllRserveOnLinux(err)
					if (err.isError) {
						err.addLocation(object {})
						throw MyException(err)
					}
					if (list.isNotEmpty())
						logger.info("cleaned up prev ${RUtil.rServeName}. pid: {}",
							list.map { it.toString() }
								.joinToString(" ")
						)
				}

				logger.info("starting ${RUtil.rServeName}...")
				mainPid = RUtil.startRserveOnLinux(err, this.rPath, this.port)
					.onNegative {
						err.addLocation(object{})
						throw MyException(err)
					}
				logger.info("main process: $mainPid initialized")
				for (i in 0 until this.rConnCount) {
					val myR = this.newMyR(err, 0)
						?: run {
							err.addLocation(object{})
							throw MyException(err)
						}
					this.availableMyR.add(myR)
					this.allMyR.add(myR)
					logger.info("MyR process: ${myR.pid} initialized")
				}

				this.mainPid = mainPid
				mainPid = -1L
				logger.info("${this.javaClass.name} initialized.")
			} catch (m: MyException) {
				throw m
			} catch (t: Throwable) {
				err.newErr(MyErr.etUnexpected, exception = t)
				throw MyException(err)
			} finally {
				if (mainPid > 0) {
					for (myR in this.allMyR) {
						myR.destroy(err, false)
							.onNegative {
								err.addLocation(object{})
								err.errCode
							}
					}
					this.allMyR.clear()
					this.availableMyR.clear()
					LinuxUtil.killProcess(err, mainPid)
						.onNegative {
							err.addLocation(object{})
							err.errCode
						}
				}
			}
		}
	}

	class OnWindows
		constructor(
			logger: Logger,
			waitForConnection: Long,
			val rServePath: String, val ports: IntArray,
			libs: Array<String>,
			devMode: Boolean
		) : RHandler2(logger, ports.size, libs, waitForConnection, devMode)
	{
		override fun shutDown(): Int {
			val err = MyErr()
			logger.info("destroying ${this.javaClass.name}...")
			ConcurrencyUtil.lock(err, this.lock, Consts.lockTimeout)
				.onNegative {
					err.addLocation(object{}).errCode
				}
			for (myR in this.allMyR) {
				val rc = myR.destroy(err, false)
					.onNegative {
						err.addLocation(object{}).errCode
					}
				logger.info("process: ${myR.pid} destroyed. rc: $rc")
			}

			this.allMyR.clear()
			this.availableMyR.clear()
			ConcurrencyUtil.unlock(err, this.lock)
				.onNegative {
					err.addLocation(object{}).errCode
				}

			if (err.isError) {
				logger.error("error while destroying ${this.javaClass.name}")
				err.log(logger)
				return err.errCode
			} else {
				logger.info("${this.javaClass.name} destroyed")
				return 1
			}
		}

		override fun newMyR(err: MyErr, port: Int): MyR? {
			return MyR.createOnWindows(err, rServePath, port, this.libs)
				?: run {
					err.addLocation(object{})
					return null
				}
		}

		val maxConnections = ports.size

		init {
			val err = MyErr()
			var ok = false
			try {
				logger.info("initializing ${this.javaClass.name}...")

				run {
					val list = ArrayList<Long>()
					for (port in this.ports) {
						val pid = WindowsUtil.findPidByPort(err, port)
							.onNegative {
								err.addLocation(object {})
								throw MyException(err)
							}
						if (pid == 0L) continue
						WindowsUtil.killProcessByPid(err, pid)
							.onNegative {
								err.addLocation(object {})
								throw MyException(err)
							}
						list.add(pid)
					}
					if (list.size > 0) {
						logger.info("cleaned up prev ${RUtil.rServeName}. pid: {}",
							list.map { it.toString() }
								.joinToString(" ")
						)
					} else {
						logger.info("no prev ${RUtil.rServeName}.")
					}
				}

				for (port in this.ports) {
					val myR = this.newMyR(err, port)
						?: run {
							err.addLocation(object{})
							err.addMsg("port: $port")
							throw MyException(err)
						}
					this.availableMyR.add(myR)
					this.allMyR.add(myR)
					logger.info("MyR process: ${myR.pid} port: ${myR.port} initialized")
				}

				ok = true
				logger.info("${this.javaClass.name} initialized.")
			} catch (m: MyException) {
				throw m
			} catch (t: Throwable) {
				err.newErr(MyErr.etUnexpected, exception = t)
				throw MyException(err)
			} finally {
				if (! ok) {
					for (myR in this.allMyR) {
						myR.destroy(err, false)
							.onNegative {
								err.addLocation(object{})
								err.errCode
							}
					}
					this.allMyR.clear()
					this.availableMyR.clear()
				}
			}
		}
	}

	////////

	protected abstract fun newMyR(err: MyErr, port: Int): MyR?

	protected val lock = ReentrantLock()
	protected val cond = lock.newCondition()
	protected val availableMyR = ArrayDeque<MyR>()
	protected val allMyR = HashSet<MyR>()

	////////

}
