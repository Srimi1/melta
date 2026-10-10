package com.melta.app.battery.creations

/**
 * The editable canvas behind the pattern editor. Plain Kotlin with no Android types,
 * so painting, flood fill, mirroring and undo are all unit tested.
 */
class PixelGrid(val size: Int) {

    val pixels = IntArray(size * size)
    private val history = ArrayDeque<IntArray>()

    /** Bumped on every change so Compose knows to redraw. */
    var version: Int = 0
        private set

    val canUndo: Boolean get() = history.isNotEmpty()

    operator fun get(x: Int, y: Int): Int = pixels[y * size + x]

    fun inBounds(x: Int, y: Int) = x in 0 until size && y in 0 until size

    /** Call once at the start of each stroke so a whole stroke undoes in one step. */
    fun checkpoint() {
        history.addLast(pixels.copyOf())
        while (history.size > MAX_UNDO) history.removeFirst()
    }

    fun undo() {
        val prev = history.removeLastOrNull() ?: return
        prev.copyInto(pixels)
        version++
    }

    fun paint(x: Int, y: Int, color: Int, mirror: Boolean) {
        if (!inBounds(x, y)) return
        var changed = set(x, y, color)
        if (mirror) changed = set(size - 1 - x, y, color) || changed
        if (changed) version++
    }

    private fun set(x: Int, y: Int, color: Int): Boolean {
        val i = y * size + x
        if (pixels[i] == color) return false
        pixels[i] = color
        return true
    }

    /** Classic 4-way bucket fill, iterative so it can never overflow the stack. */
    fun fill(x: Int, y: Int, color: Int) {
        if (!inBounds(x, y)) return
        val target = this[x, y]
        if (target == color) return
        val stack = ArrayDeque<Int>()
        stack.addLast(y * size + x)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (pixels[i] != target) continue
            pixels[i] = color
            val px = i % size
            val py = i / size
            if (px > 0) stack.addLast(i - 1)
            if (px < size - 1) stack.addLast(i + 1)
            if (py > 0) stack.addLast(i - size)
            if (py < size - 1) stack.addLast(i + size)
        }
        version++
    }

    fun clear() {
        pixels.fill(0)
        version++
    }

    fun load(source: IntArray) {
        require(source.size == pixels.size)
        source.copyInto(pixels)
        version++
    }

    val isEmpty: Boolean get() = pixels.all { it ushr 24 == 0 }

    companion object {
        const val MAX_UNDO = 40
    }
}
