package com.brainzsquare.bcommons.error


inline fun Int.onNegative(block: (Int)->Int) : Int
    = if (this >= 0) {
        this
    } else {
        block(this)
    }

inline fun Long.onNegative(block: (Long)->Long) : Long
    = if (this >= 0) {
        this
    } else {
        block(this)
    }

inline fun Int.onNonPositive(block: (Int)->Int) : Int
    = if (this > 0) {
        this
    } else {
        block(this)
    }

inline fun Long.onNonPositive(block: (Long)->Long) : Long
    = if (this > 0L) {
        this
    } else {
        block(this)
    }
