package org.anti_ad.mc.common.events

import org.anti_ad.mc.common.vanilla.Vanilla
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

object TicksDispatcher {

    private val rwLock: ReadWriteLock = ReentrantReadWriteLock(true)

    enum class ActionType {
        PRE,
        POST,
    }

    private val pre = mutableListOf<()->Unit>()
    private val post = mutableListOf<()->Unit>()


    fun register(type: ActionType, action: ()->Unit) {
        when (type) {
            ActionType.PRE -> {
                rwLock.writeLock().withLock {
                    pre.add(action)
                }
            }
            ActionType.POST -> {
                rwLock.writeLock().withLock {
                    post.add(action)
                }
            }
        }
    }

    fun dispatchPre() {
        @Suppress("SENSELESS_COMPARISON")
        if (Vanilla.mc() != null) {
            val l = rwLock.readLock().withLock {
                pre.toList()
            }
            l.forEach {
                it()
            }
        }
    }

    fun dispatchPost() {
        @Suppress("SENSELESS_COMPARISON")
        if (Vanilla.mc() != null) {
            val l = rwLock.readLock().withLock {
                pre.toList()
            }
            l.forEach {
                it()
            }
        }
    }

    fun removePre(action: () -> Unit) {
        rwLock.writeLock().withLock {
            pre.remove(action)
        }
    }

    fun removePost(action: () -> Unit) {
        rwLock.writeLock().withLock {
            post.remove(action)
        }
    }

}
