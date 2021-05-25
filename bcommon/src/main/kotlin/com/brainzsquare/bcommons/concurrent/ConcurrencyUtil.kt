package com.brainzsquare.bcommons.concurrent;

import com.brainzsquare.bcommons.error.MyErr
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock


object ConcurrencyUtil {
    val ecTimeout = MyErr.register("ecTimeout")

    /**
     * @return  {@code 1} OK
     *          {@code -1} timeout
     *          {@code -2} interrupted
     */
    fun lock(err: MyErr, lock: Lock, timeout: Long): Int {
        try {
            if (!lock.tryLock(timeout, TimeUnit.MILLISECONDS)) {
                err.newErrGeneral("cannot get lock $lock. timeout: $timeout", locObj = object {})
                return err.errCode
            }
            return 1
        } catch (e: InterruptedException) {
            err.newErr(MyErr.etInterrupted, "interrupted during tryLock $lock", locObj = e)
            return err.errCode
        }
    }

    fun unlock(err: MyErr, lock: Lock): Int {
        try {
            lock.unlock()
            return 1
        } catch (t: Throwable) {
            err.newErr(MyErr.etUnspecified, "error during unlock $lock $t", exception = t)
            return err.errCode
        }
    }

    fun await(err: MyErr, cond: Condition, timeout: Long): Int {
        try {
            return if (cond.await(timeout, TimeUnit.MILLISECONDS)) 1 else 0
        } catch (e: InterruptedException) {
            err.newErr(MyErr.etInterrupted, "interrupted during await $cond", exception = e)
            return err.errCode
        }
    }

    fun signalAll(err: MyErr, cond: Condition): Int {
        try {
            cond.signalAll()
            return 1
        } catch (t: Throwable) {
            err.newErrUnexpected("while signalling", exception = t)
            return err.errCode
        }
    }
}
