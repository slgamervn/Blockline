package com.blockline.app.ui

/**
 * Determines whether a tab-close action must first synchronize the active editor buffer.
 *
 * The native EditText snapshot is debounced. Closing the active tab must flush it before
 * the ViewModel saves and removes the tab, otherwise the last edit can be lost.
 */
internal fun shouldFlushEditorBeforeTabClose(
    closingPath: String,
    activePath: String?,
    readOnly: Boolean,
    isOpeningFile: Boolean
): Boolean {
    return closingPath == activePath && !readOnly && !isOpeningFile
}
