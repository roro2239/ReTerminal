package com.termux.terminal

internal class ByteQueue(size: Int) {
    private val buffer: ByteArray = ByteArray(size)
    private var head = 0
    private var storedBytes = 0
    private var open = true

    @Synchronized
    fun close() {
        open = false
        (this as Object).notify()
    }

    @Synchronized
    fun read(target: ByteArray, block: Boolean): Int {
        while (storedBytes == 0 && open) {
            if (block) {
                try {
                    (this as Object).wait()
                } catch (_: InterruptedException) {
                }
            } else {
                return 0
            }
        }
        if (!open) return -1

        var totalRead = 0
        val bufferLength = buffer.size
        val wasFull = bufferLength == storedBytes
        var length = target.size
        var offset = 0
        while (length > 0 && storedBytes > 0) {
            val oneRun = minOf(bufferLength - head, storedBytes)
            val bytesToCopy = minOf(length, oneRun)
            System.arraycopy(buffer, head, target, offset, bytesToCopy)
            head += bytesToCopy
            if (head >= bufferLength) head = 0
            storedBytes -= bytesToCopy
            length -= bytesToCopy
            offset += bytesToCopy
            totalRead += bytesToCopy
        }
        if (wasFull) {
            (this as Object).notify()
        }
        return totalRead
    }

    fun write(source: ByteArray, offsetStart: Int, lengthToWriteStart: Int): Boolean {
        var offset = offsetStart
        var lengthToWrite = lengthToWriteStart
        require(lengthToWrite + offset <= source.size) { "length + offset > buffer.length" }
        require(lengthToWrite > 0) { "length <= 0" }

        val bufferLength = buffer.size
        synchronized(this) {
            while (lengthToWrite > 0) {
                while (bufferLength == storedBytes && open) {
                    try {
                        (this as Object).wait()
                    } catch (_: InterruptedException) {
                    }
                }
                if (!open) return false
                val wasEmpty = storedBytes == 0
                var bytesToWriteBeforeWaiting = minOf(lengthToWrite, bufferLength - storedBytes)
                lengthToWrite -= bytesToWriteBeforeWaiting

                while (bytesToWriteBeforeWaiting > 0) {
                    var tail = head + storedBytes
                    val oneRun: Int
                    if (tail >= bufferLength) {
                        tail -= bufferLength
                        oneRun = head - tail
                    } else {
                        oneRun = bufferLength - tail
                    }
                    val bytesToCopy = minOf(oneRun, bytesToWriteBeforeWaiting)
                    System.arraycopy(source, offset, buffer, tail, bytesToCopy)
                    offset += bytesToCopy
                    bytesToWriteBeforeWaiting -= bytesToCopy
                    storedBytes += bytesToCopy
                }
                if (wasEmpty) {
                    (this as Object).notify()
                }
            }
        }
        return true
    }
}
