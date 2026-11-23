package com.example.util

import android.content.Context
import android.os.PowerManager
import android.util.Log

import java.util.concurrent.atomic.AtomicInteger

/**
 * Acquires a partial WakeLock while long-running OpenClaw requests are in flight
 * so Android does not sleep the CPU mid-stream when the screen dims or turns off.
 * Reference-counted internally so multiple concurrent Bot Dude / Cafe / General requests
 * do not release the WakeLock while other lanes are still streaming.
 */
class WakeLockHelper(context: Context) {
  private val powerManager =
    context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

  private val lock = powerManager?.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "ClawDudebro:OpenClawRequest"
  )?.apply {
    setReferenceCounted(false)
  }

  private val activeHolders = AtomicInteger(0)

  suspend fun <T> withPartialWakeLock(
    timeoutMs: Long = 15 * 60 * 1000L,
    block: suspend () -> T,
  ): T {
    activeHolders.incrementAndGet()
    try {
      if (lock != null && !lock.isHeld) {
        lock.acquire(timeoutMs)
      }
    } catch (e: Exception) {
      Log.w("WakeLockHelper", "Could not acquire WakeLock: ${e.message}")
    }
    return try {
      block()
    } finally {
      releaseSafely()
    }
  }

  fun releaseSafely() {
    val remaining = activeHolders.updateAndGet { cur -> (cur - 1).coerceAtLeast(0) }
    if (remaining > 0) return
    try {
      if (lock != null && lock.isHeld) {
        lock.release()
      }
    } catch (e: Exception) {
      Log.w("WakeLockHelper", "Could not release WakeLock: ${e.message}")
    }
  }

  fun forceReleaseAll() {
    activeHolders.set(0)
    try {
      if (lock != null && lock.isHeld) {
        lock.release()
      }
    } catch (e: Exception) {
      Log.w("WakeLockHelper", "Could not force-release WakeLock: ${e.message}")
    }
  }
}
