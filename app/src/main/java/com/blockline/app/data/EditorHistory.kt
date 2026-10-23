package com.blockline.app.data

class EditorHistory(
    private val maxDepth: Int = 80,
    private val groupingWindowMillis: Long = 700L,
    private val maxCharsPerStack: Long = 1_000_000L
) {
    private val undo = mutableMapOf<String, MutableList<String>>()
    private val redo = mutableMapOf<String, MutableList<String>>()
    private val lastEditAt = mutableMapOf<String, Long>()

    init {
        require(maxDepth > 0)
        require(groupingWindowMillis >= 0L)
        require(maxCharsPerStack > 0L)
    }

    fun recordEdit(path: String, previousContent: String, nowMillis: Long) {
        val lastEdit = lastEditAt[path] ?: 0L
        if (lastEdit == 0L || nowMillis - lastEdit > groupingWindowMillis) {
            pushHistory(undo.getOrPut(path) { mutableListOf() }, previousContent)
        }
        redo.getOrPut(path) { mutableListOf() }.clear()
        lastEditAt[path] = nowMillis
    }

    fun undo(path: String, currentContent: String): String? {
        val stack = undo[path] ?: return null
        if (stack.isEmpty()) return null
        pushHistory(redo.getOrPut(path) { mutableListOf() }, currentContent)
        lastEditAt[path] = 0L
        return stack.removeAt(stack.lastIndex)
    }

    fun redo(path: String, currentContent: String): String? {
        val stack = redo[path] ?: return null
        if (stack.isEmpty()) return null
        pushHistory(undo.getOrPut(path) { mutableListOf() }, currentContent)
        lastEditAt[path] = 0L
        return stack.removeAt(stack.lastIndex)
    }

    fun reset(path: String) {
        undo.remove(path)
        redo.remove(path)
        lastEditAt.remove(path)
    }

    fun clear() {
        undo.clear()
        redo.clear()
        lastEditAt.clear()
    }

    fun remap(oldPath: String, newPath: String) {
        if (oldPath == newPath) return
        undo.remove(oldPath)?.let { undo[newPath] = it }
        redo.remove(oldPath)?.let { redo[newPath] = it }
        lastEditAt.remove(oldPath)?.let { lastEditAt[newPath] = it }
    }

    private fun pushHistory(stack: MutableList<String>, value: String) {
        if (value.length.toLong() > maxCharsPerStack) {
            stack.clear()
            return
        }
        stack.add(value)
        var storedChars = stack.sumOf { it.length.toLong() }
        while (stack.size > maxDepth || storedChars > maxCharsPerStack) {
            storedChars -= stack.removeAt(0).length.toLong()
        }
    }
}
