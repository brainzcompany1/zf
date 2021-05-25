package com.brainzsquare.zf.r

import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.error.onNegative
import com.brainzsquare.bcommons.error.onNonPositive
import com.brainzsquare.bcommons.misc.TimeUtil
import com.brainzsquare.bcommons.os.ProcessUtil
import com.brainzsquare.zf.config.Consts
import org.rosuda.REngine.Rserve.RConnection


abstract class MyR
	protected constructor(val rconn: RConnection, val port: Int, val pid: Long)
{
	companion object {
		fun createOnLinux(err: MyErr, _port: Int, libs: Array<String>): MyR? {
			val port = if (_port == 0) Consts.defaultRPort else _port
			val rconn = runCatching {
				RConnection(localAddr, port)
			}.onFailure {
				err.newErr(MyErr.etUnexpected, exception = it)
				return null
			}.getOrNull()!!
			var pid = -1L
			try {
				TimeUtil.sleep(err, initWaitTime)
					.onNegative {
						err.addLocation(object {})
						return null
					}
				pid = this.getPid(err, rconn).onNonPositive {
					err.addLocation(object {})
					return null
				}
				this.loadLibraries(err, rconn, libs)
					.onNegative {
						err.addLocation(object {})
						return null
					}
			} finally {
				if (err.isError) {
					RUtil.destroyOnLinux(err, rconn, pid, false)
				}
			}
			return MyROnLinux(rconn, port, pid)
		}

		fun createOnWindows(err: MyErr, rServePath: String, port: Int, libs: Array<String>): MyR? {
			val process = RUtil.startRserveOnWindows(err, rServePath, port)
				?: run {
					err.addLocation(object{})
					return null
				}
			try {
				TimeUtil.sleep(err, initWaitTime)
					.onNegative {
						err.addLocation(object {})
						return null
					}
				val rconn = runCatching {
					RConnection(localAddr, port)
				}.onFailure {
					err.newErr(MyErr.etUnexpected, exception = it)
					return null
				}.getOrNull()!!
				val pid = this.getPid(err, rconn).onNonPositive {
					err.addLocation(object {})
					return null
				}
				this.loadLibraries(err, rconn, libs)
					.onNegative {
						err.addLocation(object {})
						return null
					}
				return MyROnWindows(rconn, port, pid, process)
			} finally {
				if (err.isError) {
					ProcessUtil.destroy(err, process)
				}
			}
		}

		////////

		private const val localAddr = "127.0.0.1"
		private const val initWaitTime = 1000L

		private fun getPid(err: MyErr, rconn: RConnection): Long {
			val rexp = RUtil.executeAndRetrieve(err, rconn, "Sys.getpid()")
				?: run {
					err.addLocation(object{})
					return err.errCode.toLong()
				}
			val pid = RUtil.getLong(err, rexp)
			if (err.isError) {
				err.addLocation(object{})
				return err.errCode.toLong()
			}
			return pid
		}

		private fun loadLibraries(err: MyErr, rconn: RConnection, libs: Array<String>): Int {
			for (lib in libs) {
				val rexp = RUtil.executeAndRetrieve(err, rconn,
					"library($lib, logical.return=TRUE, quietly=TRUE)")
					?: run {
						err.addLocation(object{})
						return err.errCode
					}
				val rc = RUtil.getInt(err, rexp)
				if (err.isError) {
					err.addLocation(object{})
					err.addMsg("while loading library: $lib. result rexp: $rexp")
					return err.errCode
				}
				if (rc == 0) {
					err.newErrGeneral("cannot find library: $lib")
					return err.errCode
				}
			}
			return 1
		}
	}

	abstract fun destroy(err: MyErr, tryShutDown: Boolean): Int

	////////

	private class MyROnLinux(rconn: RConnection, port: Int, pid: Long)
		: MyR(rconn, port, pid)
	{
		/**
		 *  @return
		 *  @see RUtil.destroyOnLinux
		 */
		override fun destroy(err: MyErr, tryShutDown: Boolean): Int {
			return RUtil.destroyOnLinux(err, this.rconn, this.pid, tryShutDown)
				.onNegative {
					err.addLocation(object{})
					return err.errCode
				}
		}
	}

	private class MyROnWindows(rconn: RConnection, port: Int, pid: Long, val process: Process)
		: MyR(rconn, port, pid)
	{
		override fun destroy(err: MyErr, tryShutDown: Boolean): Int {
			return RUtil.destroyOnWindows(err, this.rconn, this.process, tryShutDown)
				.onNegative {
					err.addLocation(object {})
					err.errCode
				}
		}
	}
}
