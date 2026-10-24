package com.blockline.app.ui

import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.widget.EditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.blockline.app.AppViewModel
import com.blockline.app.R
import com.blockline.app.data.JsonTools
import com.blockline.app.data.AddonCompletionEngine
import com.blockline.app.data.ProjectValidator
import com.blockline.app.data.ValidationIssue
import com.blockline.app.ui.theme.Emerald
import com.blockline.app.ui.theme.OnSurface
import com.blockline.app.ui.theme.EditorTokens
import com.blockline.app.ui.theme.editorTokens

private const val LARGE_FILE_PAGE_CHARS = 12_000
private const val LIVE_TEXT_ANALYSIS_MAX_CHARS = 12_000

private val COMPLETION_TOKEN_REGEX = Regex("[A-Za-z0-9_$:.-]*$")
private val MANIFEST_UUID_START_REGEX = Regex("\"uuid\"\\s*:\\s*\"$")
private val MANIFEST_FORMAT_VERSION_START_REGEX = Regex("\"format_version\"\\s*:\\s*\\d*$")

private class EditorCodeSpan(
    private val color: Int?,
    private val bold: Boolean,
    private val italic: Boolean,
    private val underline: Boolean,
    private val strikeThrough: Boolean
) : CharacterStyle() {
    override fun updateDrawState(textPaint: TextPaint) {
        color?.let { textPaint.color = it }
        textPaint.isFakeBoldText = bold
        textPaint.textSkewX = if (italic) -0.25f else 0f
        textPaint.isUnderlineText = underline
        textPaint.isStrikeThruText = strikeThrough
    }
}

private const val MAX_VISIBLE_SYNTAX_CHARS = 18_000

private data class VisibleCodeWindow(val start: Int, val text: String) {
    fun endExclusive(): Int = start + text.length
}

private class EditorSyntaxWindowTracker {
    var source: String? = null
    var start: Int = -1
    var end: Int = -1

    fun reset() {
        source = null
        start = -1
        end = -1
    }
}

private fun visibleCodeWindow(editor: EditText, source: String): VisibleCodeWindow? {
    val layout = editor.layout ?: return null
    if (source.isEmpty() || layout.lineCount == 0 || editor.height <= 0) return null
    val maxVertical = (layout.height - 1).coerceAtLeast(0)
    val firstVisible = layout.getLineForVertical(editor.scrollY.coerceIn(0, maxVertical))
    val lastVisible = layout.getLineForVertical((editor.scrollY + editor.height).coerceIn(0, maxVertical))
    val firstLine = (firstVisible - 4).coerceAtLeast(0)
    val lastLine = (lastVisible + 10).coerceAtMost(layout.lineCount - 1)
    var start = layout.getLineStart(firstLine).coerceIn(0, source.length)
    var end = layout.getLineEnd(lastLine).coerceIn(start, source.length)

    // Keep tokenizer input aligned to complete logical lines. Starting in the middle
    // of a huge minified line loses quote/comment state and produces incorrect colors.
    if (end - start > MAX_VISIBLE_SYNTAX_CHARS) {
        val visibleStart = layout.getLineStart(firstVisible).coerceIn(0, source.length)
        val visibleEnd = layout.getLineEnd(lastVisible).coerceIn(visibleStart, source.length)
        val visibleLength = visibleEnd - visibleStart

        if (visibleLength >= MAX_VISIBLE_SYNTAX_CHARS) {
            // A single oversized logical line (common in minified JSON) cannot be
            // tokenized safely as an arbitrary substring. Suppress coloring for this
            // viewport rather than painting misleading spans; normal editing stays active.
            val lineStart = layout.getLineStart(firstVisible).coerceIn(0, source.length)
            val lineEnd = layout.getLineEnd(lastVisible).coerceIn(lineStart, source.length)
            if (lineEnd - lineStart >= MAX_VISIBLE_SYNTAX_CHARS) return null
            start = lineStart
            end = lineEnd
        } else {
            // Expand only across complete lines; never cut a string/comment at the edges.
            start = visibleStart
            end = visibleEnd
            var beforeLine = firstVisible - 1
            while (beforeLine >= 0) {
                val candidateStart = layout.getLineStart(beforeLine).coerceIn(0, source.length)
                if (end - candidateStart > MAX_VISIBLE_SYNTAX_CHARS) break
                start = candidateStart
                beforeLine--
            }
            var afterLine = lastVisible + 1
            while (afterLine < layout.lineCount) {
                val candidateEnd = layout.getLineEnd(afterLine).coerceIn(start, source.length)
                if (candidateEnd - start > MAX_VISIBLE_SYNTAX_CHARS) break
                end = candidateEnd
                afterLine++
            }
        }
    }
    if (start >= end) return null
    return VisibleCodeWindow(start, source.substring(start, end))
}

private fun clearEditorCodeSpans(editor: EditText) {
    val editable = editor.text ?: return
    editable.getSpans(0, editable.length, EditorCodeSpan::class.java).forEach(editable::removeSpan)
}

private fun applyEditorCodeSpans(
    editor: EditText,
    source: String,
    windowStart: Int,
    windowText: String,
    highlighted: AnnotatedString,
    tracker: EditorSyntaxWindowTracker
) {
    val editable = editor.text ?: return
    val windowEnd = windowStart + windowText.length
    if (windowStart < 0 || windowEnd > editable.length) return
    if (editable.subSequence(windowStart, windowEnd).toString() != windowText) return

    // Text edits move existing spans, so clear once per source snapshot. During ordinary
    // scrolling, remove only the old viewport's spans instead of querying the full document.
    if (tracker.source !== source) {
        clearEditorCodeSpans(editor)
        tracker.source = source
        tracker.start = -1
        tracker.end = -1
    } else if (tracker.start >= 0 && tracker.end > tracker.start) {
        val oldStart = tracker.start.coerceIn(0, editable.length)
        val oldEnd = tracker.end.coerceIn(oldStart, editable.length)
        if (oldStart < oldEnd) {
            editable.getSpans(oldStart, oldEnd, EditorCodeSpan::class.java)
                .forEach(editable::removeSpan)
        }
    }

    editor.beginBatchEdit()
    try {
        highlighted.spanStyles.forEach { range ->
            val style = range.item
            val color = style.color.takeUnless { it == Color.Unspecified }?.toArgb()
            // Large-file highlighting must not change glyph metrics. Bold/italic and
            // decoration spans can alter wrapped-line geometry, which makes EditText
            // recalculate its layout while scrolling and can snap the viewport upward.
            val start = (windowStart + range.start).coerceIn(windowStart, windowEnd)
            val end = (windowStart + range.end).coerceIn(start, windowEnd)
            if (color != null && start < end) {
                editable.setSpan(
                    EditorCodeSpan(color, false, false, false, false),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    } finally {
        editor.endBatchEdit()
    }

    tracker.source = source
    tracker.start = windowStart
    tracker.end = windowEnd
}

private fun paletteFor(tokens: EditorTokens) = Palette(
    key = tokens.tokenKey,
    string = tokens.tokenString,
    number = tokens.tokenNumber,
    keyword = tokens.tokenKeyword,
    comment = tokens.tokenComment,
    errorBackground = tokens.errorBackground,
    boolean = tokens.tokenKeyword,
    function = tokens.tokenFunction,
    property = tokens.tokenKey,
    identifier = tokens.foreground
)

private fun largeFilePageOffsets(text: String): List<Int> {
    if (text.isEmpty()) return listOf(0)
    val offsets = ArrayList<Int>((text.length / LARGE_FILE_PAGE_CHARS) + 1)
    offsets += 0
    var offset = LARGE_FILE_PAGE_CHARS
    while (offset < text.length) {
        if (offset > 0 && Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset])) {
            offset++
        }
        if (offset >= text.length) break
        offsets += offset
        offset += LARGE_FILE_PAGE_CHARS
    }
    return offsets
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val path = vm.activeFile
    val tokens = editorTokens(vm.themeMode, systemDark = isSystemInDarkTheme())
    val clickSound = remember(context, vm.clickSoundsEnabled) {
        if (vm.clickSoundsEnabled) runCatching { PixelClickSound(context) }.getOrNull() else null
    }
    DisposableEffect(clickSound) {
        onDispose { clickSound?.close() }
    }
    val playClickSound = { if (vm.clickSoundsEnabled) clickSound?.play() }
    val wideLayout = LocalConfiguration.current.screenWidthDp >= 600
    val keyboardController = LocalSoftwareKeyboardController.current
    var sidebarOpen by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var commandQuery by remember { mutableStateOf("") }
    var showQuickSettings by remember { mutableStateOf(false) }
    var showGoToLine by remember { mutableStateOf(false) }
    var showAdvancementToast by remember { mutableStateOf(false) }
    var explorerFolder by remember { mutableStateOf("") }
    var goToLineText by remember { mutableStateOf("1") }
    val projectId = vm.current?.id.orEmpty()
    val goToFileLabel = stringResource(R.string.chrome_go_to_file)
    val goToLineLabel = stringResource(R.string.chrome_go_to_line)
    val findCommandLabel = stringResource(R.string.chrome_find_command)
    val formatCommandLabel = stringResource(R.string.chrome_format_document)
    val saveCommandLabel = stringResource(R.string.chrome_save_command)
    val filePrefix = stringResource(R.string.chrome_file_prefix)
    var indexedFiles by remember(projectId) { mutableStateOf<List<String>>(emptyList()) }
    var commandCandidates by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(projectId, vm.projectEntries[projectId]) {
        val sourceFiles = vm.projectEntries[projectId].orEmpty()
        val mapped = withContext(Dispatchers.Default) {
            sourceFiles.asSequence()
                .filter { it.isFile }
                .map { vm.repo.relative(projectId, it) }
                .sorted()
                .toList()
        }
        if (projectId == vm.current?.id.orEmpty()) indexedFiles = mapped
    }
    val explorerItems = remember(indexedFiles, explorerFolder) {
        if (explorerFolder.isBlank()) indexedFiles
        else indexedFiles.filter { it.startsWith("$explorerFolder/") }
    }
    LaunchedEffect(indexedFiles, vm.openTabs.keys.toList(), goToFileLabel, goToLineLabel, findCommandLabel, formatCommandLabel, saveCommandLabel, filePrefix) {
        val openTabs = vm.openTabs.keys.toList()
        commandCandidates = withContext(Dispatchers.Default) {
            listOf(goToFileLabel, goToLineLabel, findCommandLabel, formatCommandLabel, saveCommandLabel) +
                (indexedFiles + openTabs.filter { it !in indexedFiles }).map { "$filePrefix $it" }
        }
    }
    var findText by remember { mutableStateOf("") }
    var showFind by remember { mutableStateOf(false) }
    var showMinecraftPalette by remember { mutableStateOf(false) }
    var showHtmlPreview by remember(path) { mutableStateOf(false) }
    var editorValue by remember(path) { mutableStateOf(TextFieldValue(if (vm.readOnly) "" else vm.content)) }
    var largeEditText by remember(path) { mutableStateOf<EditText?>(null) }
    val syntaxRefreshRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    var highlightedText by remember(path) { mutableStateOf<Pair<String, AnnotatedString>?>(null) }
    val syntaxWindowTracker = remember(path) { EditorSyntaxWindowTracker() }
    var largeFilePage by remember(path) { mutableStateOf(0) }
    var matchCount by remember(path) { mutableStateOf(0) }
    val lang = path?.substringAfterLast('.', "")?.let { langFor(it) }
    var syntaxIssue by remember(path) { mutableStateOf<ValidationIssue?>(null) }
    var checkingJson by remember(path) { mutableStateOf(false) }
    val completionEngine = remember(context) { runCatching { AddonCompletionEngine.load(context) }.getOrNull() }
    var completionItems by remember(path) { mutableStateOf<List<AddonCompletionEngine.Suggestion>>(emptyList()) }
    var completionRange by remember(path) { mutableStateOf(0 to 0) }
    LaunchedEffect(vm.advancementEvent) {
        if (vm.advancementEvent > 0) {
            showAdvancementToast = true
            delay(2_000)
            showAdvancementToast = false
        }
    }
    val largeOffsets = remember(vm.content, vm.readOnly) {
        if (vm.readOnly) largeFilePageOffsets(vm.content) else listOf(0)
    }
    val pageIndex = largeFilePage.coerceIn(0, (largeOffsets.size - 1).coerceAtLeast(0))
    val pageStart = largeOffsets[pageIndex]
    val pageEnd = largeOffsets.getOrNull(pageIndex + 1) ?: vm.content.length
    val pageText = remember(vm.content, pageStart, pageEnd, vm.readOnly) {
        if (vm.readOnly) {
            vm.content.substring(pageStart.coerceAtMost(vm.content.length), pageEnd.coerceAtMost(vm.content.length))
        } else {
            ""
        }
    }
    var highlightedPage by remember(path) { mutableStateOf<Pair<String, AnnotatedString>?>(null) }
    LaunchedEffect(pageText, lang, tokens) {
        if (lang == null || pageText.isEmpty()) {
            highlightedPage = null
        } else {
            val source = pageText
            val palette = paletteFor(tokens)
            val result = withContext(Dispatchers.Default) { highlight(source, lang, palette) }
            if (source == pageText) highlightedPage = source to result
        }
    }
    val pageDisplayText = remember(pageText, highlightedPage) {
        highlightedPage?.takeIf { it.first == pageText }?.second ?: AnnotatedString(pageText)
    }

    LaunchedEffect(showFind, findText, path, vm.content) {
        if (!showFind || findText.isBlank()) {
            matchCount = 0
        } else {
            delay(250)
            val query = findText
            val source = vm.content
            val count = withContext(Dispatchers.Default) {
                Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(source).count()
            }
            if (query == findText) matchCount = count
        }
    }
    LaunchedEffect(path, editorValue.text, editorValue.selection, vm.readOnly, completionEngine) {
        val engine = completionEngine
        val activePath = path
        if (
            engine == null || activePath == null || vm.readOnly || vm.isOpeningFile ||
            editorValue.text.length > LIVE_TEXT_ANALYSIS_MAX_CHARS
        ) {
            completionItems = emptyList()
            return@LaunchedEffect
        }
        if (completionItems.isNotEmpty()) completionItems = emptyList()
        val snapshotText = editorValue.text
        val snapshotCursor = editorValue.selection.start.coerceIn(0, snapshotText.length)
        val projectKey = "${vm.current?.id ?: "unknown"}:$activePath"
        delay(140)
        val result = withContext(Dispatchers.Default) {
            val beforeCursor = snapshotText.substring(0, snapshotCursor)
            val currentToken = COMPLETION_TOKEN_REGEX.find(beforeCursor)?.value.orEmpty()
            val isManifest = activePath.substringAfterLast('/').equals("manifest.json", true)
            val isManifestUuidStart = isManifest && MANIFEST_UUID_START_REGEX.containsMatchIn(beforeCursor)
            val isManifestFormatVersionStart =
                isManifest && MANIFEST_FORMAT_VERSION_START_REGEX.containsMatchIn(beforeCursor)
            val shouldSuggest = currentToken.isNotEmpty() || beforeCursor.endsWith(".") ||
                isManifestUuidStart || isManifestFormatVersionStart ||
                (isManifest && beforeCursor.trimEnd().endsWith("{"))
            if (shouldSuggest) {
                engine.suggest(
                    AddonCompletionEngine.Request(activePath, snapshotText, snapshotCursor, projectKey)
                )
            } else {
                null
            }
        }
        if (activePath == vm.activeFile && snapshotText == editorValue.text &&
            snapshotCursor == editorValue.selection.start
        ) {
            if (result == null) {
                completionItems = emptyList()
            } else {
                completionRange = result.start to result.end
                completionItems = result.items
            }
        }
    }

    LaunchedEffect(path, editorValue.text, vm.readOnly, vm.isOpeningFile) {
        if (path == null || vm.readOnly || vm.isOpeningFile || editorValue.text == vm.content) return@LaunchedEffect
        val snapshot = editorValue.text
        delay(260)
        if (path == vm.activeFile && !vm.readOnly && !vm.isOpeningFile &&
            editorValue.text == snapshot && vm.content != snapshot
        ) {
            vm.updateContent(snapshot)
        }
    }
    LaunchedEffect(path, editorValue.text, lang, vm.readOnly) {
        if ((lang != Lang.JSON && lang != Lang.LANG) || vm.readOnly || editorValue.text.isBlank() ||
            editorValue.text.length > LIVE_TEXT_ANALYSIS_MAX_CHARS
        ) {
            syntaxIssue = null
            checkingJson = false
        } else {
            checkingJson = true
            delay(320)
            val source = editorValue.text
            val issue = withContext(Dispatchers.Default) {
                if (lang == Lang.LANG) ProjectValidator.inspectLangSyntax(source)
                else ProjectValidator.inspectJsonSyntax(source)
            }
            if (source == editorValue.text && path == vm.activeFile) {
                syntaxIssue = issue
                checkingJson = false
            }
        }
    }
    val jsonErrorOffset = remember(editorValue.text, syntaxIssue) {
        syntaxIssue?.line?.let { targetLine ->
            var currentLine = 1
            var offset = 0
            while (offset < editorValue.text.length && currentLine < targetLine) {
                if (editorValue.text[offset] == '\n') currentLine++
                offset++
            }
            (offset + ((syntaxIssue?.column ?: 1) - 1)).coerceIn(0, editorValue.text.length)
        }
    }
    LaunchedEffect(path, editorValue.text, lang, tokens, largeEditText, vm.readOnly) {
        val source = editorValue.text
        val activePath = path
        val native = largeEditText
        if (activePath == null || lang == null || source.isEmpty() || vm.readOnly) {
            highlightedText = null
            if (native != null) {
                withContext(Dispatchers.Main.immediate) { clearEditorCodeSpans(native) }
            }
            syntaxWindowTracker.reset()
            return@LaunchedEffect
        }

        val palette = paletteFor(tokens)
        if (source.length <= LIVE_TEXT_ANALYSIS_MAX_CHARS) {
            if (native != null) {
                withContext(Dispatchers.Main.immediate) { clearEditorCodeSpans(native) }
            }
            syntaxWindowTracker.reset()
            delay(150)
            val colored = withContext(Dispatchers.Default) { highlight(source, lang, palette) }
            if (activePath == vm.activeFile && source === editorValue.text) {
                highlightedText = source to colored
            }
        } else {
            highlightedText = null
            val editor = native ?: return@LaunchedEffect
            // Debounce content edits briefly; viewport scrolling is handled by the
            // separate throttled effect below and does not wait for this delay.
            delay(90)
            if (activePath != vm.activeFile || source !== editorValue.text || editor !== largeEditText) {
                return@LaunchedEffect
            }
            val window = withContext(Dispatchers.Main.immediate) {
                visibleCodeWindow(editor, source)
            }
            if (window == null) {
                // Keep the last painted viewport while layout/tokenization is temporarily unavailable.
                // Clearing all spans here on every scroll frame causes visible color blinking.
                return@LaunchedEffect
            }
            val coloredWindow = withContext(Dispatchers.Default) {
                highlight(window.text, lang, palette)
            }
            withContext(Dispatchers.Main.immediate) {
                val currentWindow = visibleCodeWindow(editor, source)
                if (
                    activePath == vm.activeFile &&
                    source === editorValue.text &&
                    editor === largeEditText &&
                    currentWindow != null &&
                    currentWindow.start == window.start &&
                    currentWindow.endExclusive() == window.endExclusive()
                ) {
                    applyEditorCodeSpans(
                        editor, source, window.start, window.text, coloredWindow, syntaxWindowTracker
                    )
                }
            }
        }
    }

    LaunchedEffect(path, lang, tokens, largeEditText, vm.readOnly) {
        val activePath = path ?: return@LaunchedEffect
        val activeLang = lang ?: return@LaunchedEffect
        if (vm.readOnly) return@LaunchedEffect
        val palette = paletteFor(tokens)

        // Keep one worker alive while scrolling. Conflate queued refresh requests instead
        // of cancelling syntax work on every scroll callback; after a pass, process the
        // newest viewport rather than painting an outdated one.
        syntaxRefreshRequests.receiveAsFlow().collect {
                val source = editorValue.text
                if (source.length <= LIVE_TEXT_ANALYSIS_MAX_CHARS) return@collect
                val editor = largeEditText ?: return@collect
                val window = withContext(Dispatchers.Main.immediate) {
                    if (source !== editorValue.text || activePath != vm.activeFile || editor !== largeEditText) {
                        null
                    } else {
                        visibleCodeWindow(editor, source)
                    }
                }
                if (window == null) {
                    // Keep the last painted viewport while layout/tokenization is temporarily unavailable.
                    // Clearing all spans here on every scroll frame causes visible color blinking.
                    return@collect
                }

                val coloredWindow = withContext(Dispatchers.Default) {
                    highlight(window.text, activeLang, palette)
                }
                withContext(Dispatchers.Main.immediate) {
                    val currentWindow = visibleCodeWindow(editor, source)
                    if (
                        activePath == vm.activeFile &&
                        source === editorValue.text &&
                        editor === largeEditText &&
                        currentWindow != null &&
                        currentWindow.start == window.start &&
                        currentWindow.endExclusive() == window.endExclusive()
                    ) {
                        applyEditorCodeSpans(
                            editor, source, window.start, window.text, coloredWindow, syntaxWindowTracker
                        )
                    }
                }
            }
    }
    val precomputedEditorText = remember(highlightedText, editorValue.text) {
        highlightedText?.takeIf { it.first == editorValue.text }?.second
    }
    LaunchedEffect(path, vm.content, vm.readOnly) {
        if (vm.readOnly) {
            if (editorValue.text.isNotEmpty()) editorValue = TextFieldValue()
        } else if (editorValue.text != vm.content) {
            val cursor = editorValue.selection.start.coerceAtMost(vm.content.length)
            editorValue = TextFieldValue(vm.content, TextRange(cursor))
        }
    }
    LaunchedEffect(path, vm.pendingEditorLine, vm.content, vm.readOnly, largeOffsets) {
        val targetLine = vm.pendingEditorLine ?: return@LaunchedEffect
        if (path == null) return@LaunchedEffect
        val offset = if (targetLine <= 1) 0 else {
            var line = 1
            var position = 0
            while (position < vm.content.length && line < targetLine) {
                if (vm.content[position] == '\n') line++
                position++
            }
            position
        }
        if (vm.readOnly) {
            largeFilePage = largeOffsets.indexOfLast { it <= offset }.coerceAtLeast(0)
        } else {
            editorValue = TextFieldValue(vm.content, TextRange(offset.coerceIn(0, vm.content.length)))
        }
        vm.consumePendingEditorLine()
    }
    fun insertAtCursor(insertion: String) {
        val native = largeEditText.takeIf { editorValue.text.length > LIVE_TEXT_ANALYSIS_MAX_CHARS }
        if (native != null) {
            val editable = native.text
            val current = editable.toString()
            val start = native.selectionStart.coerceIn(0, current.length)
            val end = native.selectionEnd.coerceIn(start, current.length)
            editable.replace(start, end, insertion)
            val cursor = (start + insertion.length).coerceAtMost(editable.length)
            native.setSelection(cursor)
            val updated = editable.toString()
            editorValue = TextFieldValue(updated, TextRange(cursor))
            vm.updateContent(updated)
        } else {
            val source = editorValue.text
            val start = editorValue.selection.start.coerceIn(0, source.length)
            val end = editorValue.selection.end.coerceIn(start, source.length)
            val updated = source.replaceRange(start, end, insertion)
            editorValue = TextFieldValue(updated, TextRange(start + insertion.length))
            vm.updateContent(updated)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.sidebarBackground),
                title = { Column { Text(path?.substringAfterLast('/') ?: stringResource(R.string.editor)); if (path != null) Text(path, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = tokens.muted) } },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.saveCurrent(showMessage = false)
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_cd)) }
                },
                actions = {
                    IconButton(onClick = { commandQuery = ""; showCommandPalette = true }) {
                        Icon(Icons.Default.Search, stringResource(R.string.chrome_command_palette))
                    }
                    IconButton(onClick = {
                        if (editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.undo()
                    }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_undo_cd)) }
                    IconButton(onClick = {
                        if (editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.redo()
                    }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.editor_redo_cd)) }
                    if (path?.substringAfterLast('.', "")?.lowercase() in setOf("html", "htm")) {
                        IconButton(onClick = { showHtmlPreview = true }, enabled = !vm.isOpeningFile) {
                            Icon(Icons.Default.Visibility, "Preview HTML")
                        }
                    }
                    IconButton(onClick = {
                        if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        showFind = true
                    }, enabled = !vm.isOpeningFile) { Icon(Icons.Default.FindInPage, stringResource(R.string.editor_find_cd)) }
                    IconButton(onClick = {
                        if (!vm.readOnly && !vm.isOpeningFile) {
                            val next = if (lang == Lang.JSON) JsonTools.format(editorValue.text) else editorValue.text
                            editorValue = TextFieldValue(next, TextRange(next.length))
                            vm.updateContent(next)
                        }
                        vm.saveCurrent()
                    }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.Default.Save, stringResource(R.string.editor_save_cd), tint = Emerald) }
                }
            )
            AnimatedVisibility(
                visible = showAdvancementToast && vm.gameEffectsEnabled,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
                        .border(1.dp, tokens.inventoryLight, RectangleShape)
                        .border(2.dp, tokens.inventoryDark, RectangleShape),
                    color = tokens.sidebarBackground,
                    shape = RectangleShape
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("◆", color = tokens.gameGreen, style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.chrome_advancement_title), color = tokens.gameGreen, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(if (vm.advancementEventIsCreation) R.string.chrome_created_file else R.string.chrome_saved_file),
                                color = tokens.foreground,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            }
        },
        bottomBar = {
            Column {
                EditorShortcutBar(
                    enabled = !vm.isOpeningFile && !vm.readOnly,
                    onInsert = { insertion -> insertAtCursor(insertion) },
                    onUndo = {
                        if (editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.undo()
                    },
                    onRedo = {
                        if (editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.redo()
                    },
                    onFormat = {
                        if (lang == Lang.JSON) {
                            val next = JsonTools.format(editorValue.text)
                            editorValue = TextFieldValue(next, TextRange(next.length))
                            vm.updateContent(next)
                        } else vm.message = context.getString(R.string.format_json_only)
                    },
                    onSave = {
                        if (!vm.isOpeningFile && !vm.readOnly && editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.saveCurrent()
                    },
                    onFind = { showFind = true },
                    onHideKeyboard = { keyboardController?.hide() },
                    onShowColorPalette = { showMinecraftPalette = true },
                    tokens = tokens,
                    gameEffectsEnabled = vm.gameEffectsEnabled,
                    onClickSound = playClickSound
                )
                val cursor = editorValue.selection.start.coerceIn(0, editorValue.text.length)
                val beforeCursor = editorValue.text.substring(0, cursor)
                val line = beforeCursor.count { it == '\n' } + 1
                val column = cursor - (beforeCursor.lastIndexOf('\n') + 1) + 1
                val languageLabel = when (lang) {
                    Lang.JSON -> "JSON"
                    Lang.JS -> "JavaScript"
                    Lang.MCFUNC -> "mcfunction"
                    Lang.LANG -> "Properties"
                    Lang.HTML -> "HTML/XML"
                    Lang.CSS -> "CSS"
                    null -> path?.substringAfterLast('.', "Plain Text")?.uppercase() ?: "Plain Text"
                }
                EditorStatusBar(
                    line = line,
                    column = column,
                    language = languageLabel,
                    saved = path == null || (editorValue.text == vm.activeFilePersistedContent && !vm.isOpeningFile),
                    tokens = tokens
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(tokens.editorBackground)) {
        Row(Modifier.fillMaxSize()) {
            if (!wideLayout) {
                Box(
                    Modifier.width(10.dp).fillMaxSize().pointerInput(sidebarOpen) {
                        var dragDistance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onDragEnd = {
                                if (dragDistance > 35f) sidebarOpen = true
                                if (dragDistance < -35f) sidebarOpen = false
                                dragDistance = 0f
                            },
                            onHorizontalDrag = { change, delta ->
                                dragDistance += delta
                                change.consume()
                            }
                        )
                    }
                )
            }
            EditorActivityBar(
                selected = when {
                    showQuickSettings -> "settings"
                    showFind -> "search"
                    sidebarOpen || wideLayout -> "explorer"
                    else -> ""
                },
                onExplorer = { sidebarOpen = !sidebarOpen },
                onSearch = { showFind = true },
                onSettings = { showQuickSettings = true },
                tokens = tokens,
                gameEffectsEnabled = vm.gameEffectsEnabled,
                onClickSound = playClickSound
            )
            if (wideLayout) {
                EditorSidebar(
                    title = stringResource(R.string.chrome_explorer),
                    items = explorerItems,
                    selectedPath = path,
                    tokens = tokens,
                    gameEffectsEnabled = vm.gameEffectsEnabled,
                    onClickSound = playClickSound,
                    onSelect = { selectedPath ->
                        if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) {
                            vm.updateContent(editorValue.text)
                        }
                        vm.openFile(selectedPath)
                    }
                )
            }
            Column(Modifier.weight(1f).fillMaxSize().padding(8.dp)) {
            if (vm.isOpeningFile) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = tokens.gameGreen,
                    trackColor = tokens.divider
                )
                Text(stringResource(R.string.opening_file), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
            if (path == null) {
                EmptyState(Icons.Default.ContentCopy, stringResource(R.string.no_file_open), stringResource(R.string.no_file_hint))
            } else {
                if (path != null) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 32.dp)
                            .background(tokens.editorBackground)
                            .padding(horizontal = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            stringResource(R.string.chrome_project),
                            modifier = Modifier.clickable {
                                explorerFolder = ""
                                sidebarOpen = true
                            },
                            color = tokens.muted,
                            fontSize = 11.sp
                        )
                        path.split('/').forEachIndexed { index, segment ->
                            Text("  ›  ", color = tokens.muted, fontSize = 11.sp)
                            Text(
                                segment,
                                modifier = Modifier.clickable(enabled = index < path.split('/').lastIndex) {
                                    explorerFolder = path.split('/').take(index + 1).joinToString("/")
                                    sidebarOpen = true
                                },
                                color = if (index == path.split('/').lastIndex) tokens.foreground else tokens.muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (vm.openTabs.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .background(tokens.sidebarBackground)
                            .border(1.dp, tokens.divider),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        vm.openTabs.keys.toList().forEach { tab ->
                            val selected = tab == path
                            val dirty = selected && editorValue.text != vm.content
                            Column(
                                Modifier
                                    .background(if (selected) tokens.tabSelected else tokens.tabInactive)
                                    .clickable {
                                        if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) {
                                            vm.updateContent(editorValue.text)
                                        }
                                        vm.openFile(tab)
                                    }
                            ) {
                                Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) tokens.statusBar else Color.Transparent))
                                Row(Modifier.heightIn(min = 42.dp).padding(start = 11.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (dirty) Text("● ", color = tokens.gameGreen, fontSize = 11.sp)
                                    Text(tab.substringAfterLast('/'), color = tokens.foreground, fontSize = 12.sp, maxLines = 1)
                                    IconButton(onClick = { vm.closeTab(tab) }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Close, stringResource(R.string.close_tab), tint = tokens.muted, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if ((lang == Lang.JSON || lang == Lang.LANG) && !vm.readOnly && editorValue.text.isNotBlank() &&
                    editorValue.text.length <= LIVE_TEXT_ANALYSIS_MAX_CHARS
                ) {
                    val issue = syntaxIssue
                    val colors = MaterialTheme.colorScheme
                    val hasSyntaxError = issue?.severity == com.blockline.app.data.IssueSeverity.ERROR
                    val hasSyntaxWarning = issue?.severity == com.blockline.app.data.IssueSeverity.WARNING
                    val statusText = when {
                        lang == Lang.LANG && checkingJson -> stringResource(R.string.lang_validation_checking)
                        lang == Lang.LANG && hasSyntaxError -> stringResource(R.string.lang_validation_invalid, issue?.line ?: 1, issue?.column ?: 1, issue?.message.orEmpty())
                        lang == Lang.LANG && hasSyntaxWarning -> stringResource(R.string.lang_validation_warning, issue?.line ?: 1, issue?.column ?: 1, issue?.message.orEmpty())
                        lang == Lang.LANG -> stringResource(R.string.lang_validation_valid)
                        checkingJson -> stringResource(R.string.json_validation_checking)
                        hasSyntaxError -> stringResource(R.string.json_validation_invalid, issue?.line ?: 1, issue?.column ?: 1, issue?.message.orEmpty())
                        hasSyntaxWarning -> stringResource(R.string.json_validation_warning, issue?.line ?: 1, issue?.column ?: 1, issue?.message.orEmpty())
                        else -> stringResource(R.string.json_validation_valid)
                    }
                    Surface(
                        color = when {
                            checkingJson -> colors.surfaceVariant
                            hasSyntaxError -> colors.errorContainer
                            else -> colors.secondaryContainer
                        },
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            statusText,
                            color = when {
                                checkingJson -> colors.onSurfaceVariant
                                hasSyntaxError -> colors.onErrorContainer
                                else -> colors.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (!vm.readOnly && lang != null && editorValue.text.length > LIVE_TEXT_ANALYSIS_MAX_CHARS) {
                    Text(
                        stringResource(R.string.editor_live_analysis_paused),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (vm.readOnly) {
                    Text(
                        stringResource(R.string.large_file_readonly),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.large_file_page, pageIndex + 1, largeOffsets.size, pageStart + 1, pageEnd, vm.content.length),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = { largeFilePage = (pageIndex - 1).coerceAtLeast(0) },
                            enabled = pageIndex > 0
                        ) { Text(stringResource(R.string.large_file_previous)) }
                        TextButton(
                            onClick = { largeFilePage = (pageIndex + 1).coerceAtMost(largeOffsets.lastIndex) },
                            enabled = pageIndex < largeOffsets.lastIndex
                        ) { Text(stringResource(R.string.large_file_next)) }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(
                            pageDisplayText,
                            style = TextStyle(
                                color = tokens.foreground,
                                fontFamily = FontFamily.Monospace,
                                fontSize = vm.fontSize.sp,
                                lineHeight = (vm.fontSize + 5).sp
                            )
                        )
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().background(tokens.editorBackground)) {
                        if (editorValue.text.length > LIVE_TEXT_ANALYSIS_MAX_CHARS) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext ->
                                    EditText(viewContext).apply {
                                        largeEditText = this
                                        var lastStyledLine = -1
                                        val syntaxRefreshRunnable = Runnable {
                                            syntaxRefreshRequests.trySend(Unit)
                                        }
                                        setOnScrollChangeListener { view, _, scrollY, _, _ ->
                                            val currentLayout = (view as? EditText)?.layout
                                            if (currentLayout != null && currentLayout.lineCount > 0) {
                                                val line = currentLayout.getLineForVertical(
                                                    scrollY.coerceIn(0, (currentLayout.height - 1).coerceAtLeast(0))
                                                )
                                                if (line != lastStyledLine) {
                                                    lastStyledLine = line
                                                    // Debounce syntax work until scrolling settles instead of
                                                    // tokenizing and replacing spans every 65 ms mid-gesture.
                                                    removeCallbacks(syntaxRefreshRunnable)
                                                    postDelayed(syntaxRefreshRunnable, 120L)
                                                }
                                            }
                                        }
                                        post { syntaxRefreshRequests.trySend(Unit) }
                                        typeface = Typeface.MONOSPACE
                                        textSize = vm.fontSize.toFloat()
                                        setTextColor(tokens.foreground.toArgb())
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        setPadding(
                                            (8 * resources.displayMetrics.density).toInt(),
                                            (8 * resources.displayMetrics.density).toInt(),
                                            (8 * resources.displayMetrics.density).toInt(),
                                            (8 * resources.displayMetrics.density).toInt()
                                        )
                                        gravity = Gravity.TOP or Gravity.START
                                        inputType = InputType.TYPE_CLASS_TEXT or
                                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                        imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                                        isVerticalScrollBarEnabled = true
                                        setHorizontallyScrolling(false)
                                        setLineSpacing(0f, 1.1f)
                                        setText(editorValue.text)
                                        setSelection(editorValue.selection.start.coerceIn(0, text.length))
                                        addTextChangedListener(object : TextWatcher {
                                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                            override fun afterTextChanged(s: Editable?) {
                                                val updated = s?.toString().orEmpty()
                                                val cursor = selectionStart.coerceIn(0, updated.length)
                                                if (updated != editorValue.text || cursor != editorValue.selection.start) {
                                                    editorValue = TextFieldValue(updated, TextRange(cursor))
                                                    if (completionItems.isNotEmpty()) completionItems = emptyList()
                                                }
                                            }
                                        })
                                    }
                                },
                                update = { editText ->
                                    if (largeEditText !== editText) largeEditText = editText
                                    if (editText.currentTextColor != tokens.foreground.toArgb()) {
                                        editText.setTextColor(tokens.foreground.toArgb())
                                    }
                                    if (editText.text.toString() != editorValue.text) {
                                        val cursor = editorValue.selection.start.coerceIn(0, editorValue.text.length)
                                        editText.setText(editorValue.text)
                                        editText.setSelection(cursor.coerceIn(0, editText.text.length))
                                    }
                                    editText.isEnabled = !vm.isOpeningFile
                                    if (editText.textSize != vm.fontSize.toFloat()) {
                                        editText.textSize = vm.fontSize.toFloat()
                                    }
                                }
                            )
                        } else {
                            BasicTextField(
                                value = editorValue,
                                onValueChange = {
                                    editorValue = it
                                    if (completionItems.isNotEmpty()) completionItems = emptyList()
                                },
                                readOnly = vm.isOpeningFile,
                                modifier = Modifier.fillMaxSize(),
                                textStyle = TextStyle(color = tokens.foreground, fontFamily = FontFamily.Monospace, fontSize = vm.fontSize.sp, lineHeight = (vm.fontSize + 5).sp),
                                visualTransformation = precomputedEditorText
                                    ?.takeIf { lang != null && editorValue.text.length <= LIVE_TEXT_ANALYSIS_MAX_CHARS }
                                    ?.let { PrecomputedHighlightTransformation(editorValue.text, it) }
                                    ?: androidx.compose.ui.text.input.VisualTransformation.None
                            )
                            DropdownMenu(
                                expanded = completionItems.isNotEmpty(),
                                onDismissRequest = { completionItems = emptyList() }
                            ) {
                                completionItems.take(12).forEach { suggestion ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(suggestion.label, style = MaterialTheme.typography.bodyMedium)
                                                if (suggestion.detail.isNotBlank()) {
                                                    Text(suggestion.detail, style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        onClick = {
                                            val start = completionRange.first.coerceIn(0, editorValue.text.length)
                                            val end = completionRange.second.coerceIn(start, editorValue.text.length)
                                            val updated = editorValue.text.replaceRange(start, end, suggestion.insertText)
                                            val cursor = start + suggestion.insertText.length
                                            editorValue = TextFieldValue(updated, TextRange(cursor))
                                            completionItems = emptyList()
                                            vm.updateContent(updated)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
        if (!wideLayout) {
            AnimatedVisibility(
                visible = sidebarOpen,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 60.dp),
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it }
            ) {
                EditorSidebar(
                    title = stringResource(R.string.chrome_explorer),
                    items = indexedFiles,
                    selectedPath = path,
                    tokens = tokens,
                    gameEffectsEnabled = vm.gameEffectsEnabled,
                    onClickSound = playClickSound,
                    onSelect = { selectedPath ->
                        if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) {
                            vm.updateContent(editorValue.text)
                        }
                        vm.openFile(selectedPath)
                        sidebarOpen = false
                    }
                )
            }
        }
        }
    }
    if (showHtmlPreview) AlertDialog(
        onDismissRequest = { showHtmlPreview = false },
        title = { Text(stringResource(R.string.html_preview_title)) },
        text = {
            AndroidView(
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 560.dp),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                },
                update = { webView ->
                    val html = editorValue.text
                    if (webView.tag != html) {
                        webView.tag = html
                        webView.loadDataWithBaseURL(
                            "https://blockline.local/",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { showHtmlPreview = false }) { Text(stringResource(R.string.close)) }
        }
    )
    if (showFind) AlertDialog(
        onDismissRequest = { showFind = false },
        title = { Text(stringResource(R.string.find_in_file)) },
        text = { Column {
            OutlinedTextField(value = findText, onValueChange = { findText = it }, label = { Text(stringResource(R.string.text_to_find)) }, singleLine = true)
            Text(if (findText.isBlank()) stringResource(R.string.enter_text_to_count) else stringResource(R.string.matches_count, matchCount))
        } },
        confirmButton = {
            TextButton(onClick = {
                val query = findText
                if (query.isNotBlank()) {
                    val source = vm.content
                    val startAt = if (vm.readOnly) pageStart.coerceIn(0, source.length) else editorValue.selection.end.coerceIn(0, source.length)
                    val found = findWrappedMatch(source, query, startAt)
                    if (found >= 0) {
                        if (vm.readOnly) {
                            largeFilePage = largeOffsets.indexOfLast { it <= found }.coerceAtLeast(0)
                        } else {
                            editorValue = editorValue.copy(selection = TextRange(found, found + query.length))
                        }
                        vm.message = context.getString(R.string.matches_count, 1)
                    } else {
                        vm.message = context.getString(R.string.find_not_found)
                    }
                }
                showFind = false
            }) { Text(stringResource(R.string.find)) }
        },
        dismissButton = { TextButton(onClick = { showFind = false }) { Text(stringResource(R.string.close)) } }
    )
    if (showCommandPalette) {
        EditorCommandPalette(
            query = commandQuery,
            onQueryChange = { commandQuery = it },
            candidates = commandCandidates,
            onDismiss = { showCommandPalette = false },
            onSelect = { command ->
                showCommandPalette = false
                when {
                    command == findCommandLabel -> showFind = true
                    command == goToLineLabel -> {
                        goToLineText = "1"
                        showGoToLine = true
                    }
                    command == formatCommandLabel -> {
                        if (!vm.readOnly && !vm.isOpeningFile && lang == Lang.JSON) {
                            val formatted = JsonTools.format(editorValue.text)
                            editorValue = TextFieldValue(formatted, TextRange(formatted.length))
                            vm.updateContent(formatted)
                        } else if (lang != Lang.JSON) vm.message = context.getString(R.string.format_json_only)
                    }
                    command == saveCommandLabel -> {
                        if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.saveCurrent()
                    }
                    command == goToFileLabel -> {
                        commandQuery = "$filePrefix "
                        showCommandPalette = true
                    }
                    command.startsWith("$filePrefix ") -> {
                        if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.openFile(command.removePrefix("$filePrefix "))
                    }
                }
            }
        )
    }
    if (showMinecraftPalette) {
        MinecraftColorPaletteDialog(
            tokens = tokens,
            onDismiss = { showMinecraftPalette = false },
            onSelectCode = { code ->
                insertAtCursor(code)
                showMinecraftPalette = false
            }
        )
    }
    if (showGoToLine) AlertDialog(
        onDismissRequest = { showGoToLine = false },
        title = { Text(stringResource(R.string.chrome_go_to_line)) },
        text = {
            OutlinedTextField(
                value = goToLineText,
                onValueChange = { goToLineText = it.filter(Char::isDigit).take(8) },
                label = { Text(stringResource(R.string.chrome_line_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val target = goToLineText.toIntOrNull()
                if (target != null && target > 0) {
                    path?.let { vm.openFile(it, target) }
                    showGoToLine = false
                } else {
                    vm.message = context.getString(R.string.chrome_enter_positive_line)
                }
            }) { Text(stringResource(R.string.chrome_go)) }
        },
        dismissButton = { TextButton(onClick = { showGoToLine = false }) { Text(stringResource(R.string.chrome_cancel)) } }
    )
    if (showQuickSettings) AlertDialog(
        onDismissRequest = { showQuickSettings = false },
        title = { Text(stringResource(R.string.chrome_quick_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.chrome_editor_theme), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { vm.themeMode = com.blockline.app.ui.theme.ThemeMode.DARK_PLUS }) { Text(stringResource(R.string.chrome_theme_dark_plus)) }
                TextButton(onClick = { vm.themeMode = com.blockline.app.ui.theme.ThemeMode.LIGHT_PLUS }) { Text(stringResource(R.string.chrome_theme_light_plus)) }
                TextButton(onClick = { vm.themeMode = com.blockline.app.ui.theme.ThemeMode.AMOLED }) { Text(stringResource(R.string.chrome_theme_amoled)) }
                TextButton(onClick = { vm.themeMode = com.blockline.app.ui.theme.ThemeMode.SYSTEM }) { Text(stringResource(R.string.chrome_theme_system)) }
            }
        },
        confirmButton = { TextButton(onClick = { showQuickSettings = false }) { Text(stringResource(R.string.ok)) } }
    )
}
