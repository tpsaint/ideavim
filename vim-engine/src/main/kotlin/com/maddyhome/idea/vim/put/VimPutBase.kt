/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.put

import com.maddyhome.idea.vim.api.BufferPosition
import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.ImmutableVimCaret
import com.maddyhome.idea.vim.api.MutableVimEditor
import com.maddyhome.idea.vim.api.VimCaret
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.getLineEndOffset
import com.maddyhome.idea.vim.api.getText
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.api.isLineEmpty
import com.maddyhome.idea.vim.api.lineLength
import com.maddyhome.idea.vim.api.moveToMotion
import com.maddyhome.idea.vim.api.setChangeMarks
import com.maddyhome.idea.vim.api.setVisualSelectionMarks
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.command.SelectionType
import com.maddyhome.idea.vim.command.VimStateMachine
import com.maddyhome.idea.vim.command.isBlock
import com.maddyhome.idea.vim.command.isChar
import com.maddyhome.idea.vim.command.isLine
import com.maddyhome.idea.vim.common.Direction
import com.maddyhome.idea.vim.common.Offset
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.common.offset
import com.maddyhome.idea.vim.diagnostic.VimLogger
import com.maddyhome.idea.vim.diagnostic.vimLogger
import com.maddyhome.idea.vim.helper.RWLockLabel
import com.maddyhome.idea.vim.helper.firstOrNull
import com.maddyhome.idea.vim.helper.inVisualMode
import com.maddyhome.idea.vim.helper.mode
import com.maddyhome.idea.vim.helper.subMode
import com.maddyhome.idea.vim.mark.VimMarkConstants.MARK_CHANGE_POS
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

public abstract class VimPutBase : VimPut {
  private fun collectPreModificationData(editor: VimEditor, visualSelection: VisualSelection?): Map<String, Any> {
    return if (visualSelection != null && visualSelection.typeInEditor.isBlock) {
      val vimSelection = visualSelection.caretsAndSelections.getValue(editor.primaryCaret())
      val selStart = editor.offsetToBufferPosition(vimSelection.vimStart)
      val selEnd = editor.offsetToBufferPosition(vimSelection.vimEnd)
      mapOf(
        "startColumnOfSelection" to min(selStart.column, selEnd.column),
        "selectedLines" to abs(selStart.line - selEnd.line),
        "firstSelectedLine" to min(selStart.line, selEnd.line),
      )
    } else {
      mutableMapOf()
    }
  }

  private fun wasTextInsertedLineWise(text: TextData): Boolean {
    return text.typeInRegister == SelectionType.LINE_WISE
  }

  /**
   * see ":h gv":
   * After using "p" or "P" in Visual mode the text that was put will be selected
   */
  private fun wrapInsertedTextWithVisualMarks(caret: VimCaret, visualSelection: VisualSelection?, textData: TextData?) {
    val textLength: Int = textData?.text?.length ?: return
    val caretsAndSelections = visualSelection?.caretsAndSelections ?: return
    val selection = caretsAndSelections[caret] ?: caretsAndSelections.firstOrNull()?.value ?: return

    val leftIndex = min(selection.vimStart, selection.vimEnd)
    val rightIndex = leftIndex + textLength - 1
    val rangeForMarks = TextRange(leftIndex, rightIndex)

    injector.markService.setVisualSelectionMarks(caret, rangeForMarks)
  }

  @RWLockLabel.SelfSynchronized
  private fun deleteSelectedText(editor: VimEditor, visualSelection: VisualSelection?, operatorArguments: OperatorArguments, saveToRegister: Boolean) {
    if (visualSelection == null) return

    visualSelection.caretsAndSelections.entries.sortedByDescending { it.key.getBufferPosition() }
      .forEach { (caret, selection) ->
        if (!caret.isValid) return@forEach
        val range = selection.toVimTextRange(false).normalize()

        injector.application.runWriteAction {
          injector.changeGroup.deleteRange(editor, caret, range, selection.type, false, operatorArguments, saveToRegister)
        }
        caret.moveToInlayAwareOffset(range.startOffset)
      }
  }

  private fun processText(caret: VimCaret?, visualSelection: VisualSelection?, textData: TextData?): TextData? {
    var text = textData?.text ?: run {
      if (caret == null) return null
      if (visualSelection != null) {
        val offset = caret.offset.point
        injector.markService.setMark(caret, MARK_CHANGE_POS, offset)
        injector.markService.setChangeMarks(caret, TextRange(offset, offset + 1))
      }
      return null
    }

    if (visualSelection?.typeInEditor?.isLine == true && textData.typeInRegister.isChar) text += "\n"

    if (textData.typeInRegister.isLine && text.isNotEmpty() && text.last() != '\n') text += '\n'

    if (textData.typeInRegister.isChar && text.lastOrNull() == '\n' && visualSelection?.typeInEditor?.isLine == false) {
      text =
        text.dropLast(1)
    }

    return TextData(
      text,
      textData.typeInRegister,
      textData.transferableData,
      textData.registerChar,
    )
  }

  public abstract fun doIndent(
    editor: VimEditor,
    caret: VimCaret,
    context: ExecutionContext,
    startOffset: Int,
    endOffset: Int,
  ): Int

  private fun putTextCharacterwise(
    editor: VimEditor,
    caret: VimCaret,
    context: ExecutionContext,
    text: String,
    type: SelectionType,
    mode: VimStateMachine.SubMode,
    startOffset: Int,
    count: Int,
    indent: Boolean,
  ): Pair<Int, VimCaret> {
    var updatedCaret = caret.moveToOffset(startOffset)
    val insertedText = text.repeat(count)
    updatedCaret = injector.changeGroup.insertText(editor, updatedCaret, insertedText)

    val endOffset = if (indent) {
      doIndent(editor, updatedCaret, context, startOffset, startOffset + insertedText.length)
    } else {
      startOffset + insertedText.length
    }
    return endOffset to updatedCaret
  }

  private fun putTextLinewise(
    editor: VimEditor,
    caret: VimCaret,
    context: ExecutionContext,
    text: String,
    type: SelectionType,
    mode: VimStateMachine.SubMode,
    startOffset: Int,
    count: Int,
    indent: Boolean,
  ): Pair<Int, VimCaret> {
    val overlappedCarets = ArrayList<VimCaret>(editor.carets().size)
    for (possiblyOverlappedCaret in editor.carets()) {
      if (possiblyOverlappedCaret.offset.point != startOffset || possiblyOverlappedCaret == caret) continue

      val updated = possiblyOverlappedCaret.moveToMotion(
        injector.motion.getHorizontalMotion(editor, possiblyOverlappedCaret, 1, true),
      )
      overlappedCarets.add(updated)
    }

    val endOffset = putTextCharacterwise(
      editor, caret, context, text, type, mode, startOffset, count, indent,
    )

    for (overlappedCaret in overlappedCarets) {
      overlappedCaret.moveToMotion(
        injector.motion.getHorizontalMotion(editor, overlappedCaret, -1, true),
      )
    }

    return endOffset
  }

  private fun getMaxSegmentLength(text: String): Int {
    val tokenizer = StringTokenizer(text, "\n")
    var maxLen = 0
    while (tokenizer.hasMoreTokens()) {
      val s = tokenizer.nextToken()
      maxLen = max(s.length, maxLen)
    }
    return maxLen
  }

  private fun putTextBlockwise(
    editor: VimEditor,
    caret: VimCaret,
    context: ExecutionContext,
    text: String,
    type: SelectionType,
    mode: VimStateMachine.SubMode,
    startOffset: Int,
    count: Int,
    indent: Boolean,
  ): Pair<Int, VimCaret> {
    val startPosition = editor.offsetToBufferPosition(startOffset)
    val currentColumn = if (mode == VimStateMachine.SubMode.VISUAL_LINE) 0 else startPosition.column
    var currentLine = startPosition.line

    val lineCount = text.getLineBreakCount() + 1
    var updated = caret
    if (currentLine + lineCount >= editor.nativeLineCount()) {
      val limit = currentLine + lineCount - editor.nativeLineCount()
      for (i in 0 until limit) {
        updated = updated.moveToOffset(editor.fileSize().toInt())
        updated = injector.changeGroup.insertText(editor, updated, "\n")
      }
    }

    val maxLen = getMaxSegmentLength(text)
    val tokenizer = StringTokenizer(text, "\n")
    var endOffset = startOffset
    while (tokenizer.hasMoreTokens()) {
      var segment = tokenizer.nextToken()
      var origSegment = segment

      if (segment.length < maxLen) {
        segment += " ".repeat(maxLen - segment.length)

        if (currentColumn != 0 && currentColumn < editor.lineLength(currentLine)) {
          origSegment = segment
        }
      }

      val pad = injector.engineEditorHelper.pad(editor, context, currentLine, currentColumn)

      val insertOffset = editor.bufferPositionToOffset(BufferPosition(currentLine, currentColumn))
      updated = updated.moveToOffset(insertOffset)
      val insertedText = origSegment + segment.repeat(count - 1)
      updated = injector.changeGroup.insertText(editor, updated, insertedText)
      endOffset += insertedText.length

      if (mode == VimStateMachine.SubMode.VISUAL_LINE) {
        updated = updated.moveToOffset(endOffset)
        updated = injector.changeGroup.insertText(editor, updated, "\n")
        ++endOffset
      } else {
        if (pad.isNotEmpty()) {
          updated = updated.moveToOffset(insertOffset)
          updated = injector.changeGroup.insertText(editor, updated, pad)
          endOffset += pad.length
        }
      }

      ++currentLine
    }
    if (indent) endOffset = doIndent(editor, updated, context, startOffset, endOffset)
    return endOffset to updated
  }

  private fun putTextInternal(
    editor: VimEditor,
    caret: VimCaret,
    context: ExecutionContext,
    text: String,
    type: SelectionType,
    mode: VimStateMachine.SubMode,
    startOffset: Int,
    count: Int,
    indent: Boolean,
  ): Pair<Int, VimCaret> {
    return when (type) {
      SelectionType.CHARACTER_WISE -> putTextCharacterwise(
        editor,
        caret,
        context,
        text,
        type,
        mode,
        startOffset,
        count,
        indent,
      )
      SelectionType.LINE_WISE -> putTextLinewise(
        editor,
        caret,
        context,
        text,
        type,
        mode,
        startOffset,
        count,
        indent,
      )
      else -> putTextBlockwise(editor, caret, context, text, type, mode, startOffset, count, indent)
    }
  }

  @RWLockLabel.SelfSynchronized
  protected fun prepareDocumentAndGetStartOffsets(
    vimEditor: VimEditor,
    vimCaret: ImmutableVimCaret,
    typeInRegister: SelectionType,
    visualSelection: VisualSelection?,
    pasteOptions: PasteOptions,
    additionalData: Map<String, Any>,
  ): List<Int> {
    // todo refactor me
    val insertTextBeforeCaret = if (pasteOptions is AtCaretPasteOptions) pasteOptions.direction == Direction.BACKWARDS else false
    val putToLine = if (pasteOptions is ToLinePasteOptions) pasteOptions.line else -1
    
    val application = injector.application
    if (visualSelection != null) {
      return when {
        visualSelection.typeInEditor.isChar && typeInRegister.isLine -> {
          application.runWriteAction { (vimEditor as MutableVimEditor).insertText(vimCaret.offset, "\n") }
          listOf(vimCaret.offset.point + 1)
        }
        visualSelection.typeInEditor.isBlock -> {
          val firstSelectedLine = additionalData["firstSelectedLine"] as Int
          val selectedLines = additionalData["selectedLines"] as Int
          val startColumnOfSelection = additionalData["startColumnOfSelection"] as Int
          val line = (if (insertTextBeforeCaret) firstSelectedLine else firstSelectedLine + selectedLines)
            .coerceAtMost(vimEditor.lineCount() - 1)
          when (typeInRegister) {
            SelectionType.LINE_WISE -> when {
              insertTextBeforeCaret -> listOf(vimEditor.getLineStartOffset(line))
              else -> {
                val pos = vimEditor.getLineEndOffset(line, true)
                application.runWriteAction { (vimEditor as MutableVimEditor).insertText(pos.offset, "\n") }
                listOf(pos + 1)
              }
            }
            SelectionType.CHARACTER_WISE -> (firstSelectedLine + selectedLines downTo firstSelectedLine)
              .map { vimEditor.bufferPositionToOffset(BufferPosition(it, startColumnOfSelection)) }
            SelectionType.BLOCK_WISE -> listOf(
              vimEditor.bufferPositionToOffset(
                BufferPosition(
                  firstSelectedLine,
                  startColumnOfSelection,
                ),
              ),
            )
          }
        }
        visualSelection.typeInEditor.isLine -> {
          val lastChar = if (vimEditor.fileSize() > 0) {
            vimEditor.getText(TextRange(vimEditor.fileSize().toInt() - 1, vimEditor.fileSize().toInt()))[0]
          } else {
            null
          }
          if (vimCaret.offset.point == vimEditor.fileSize().toInt() && vimEditor.fileSize().toInt() != 0 && lastChar != '\n') {
            application.runWriteAction { (vimEditor as MutableVimEditor).insertText(vimCaret.offset, "\n") }
            listOf(vimCaret.offset.point + 1)
          } else {
            listOf(vimCaret.offset.point)
          }
        }
        else -> listOf(vimCaret.offset.point)
      }
    } else {
      if (insertTextBeforeCaret) {
        return when (typeInRegister) {
          SelectionType.LINE_WISE -> listOf(injector.motion.moveCaretToCurrentLineStart(vimEditor, vimCaret))
          else -> listOf(vimCaret.offset.point)
        }
      }

      var startOffset: Int
      val line = if (putToLine < 0) vimCaret.getBufferPosition().line else putToLine
      when (typeInRegister) {
        SelectionType.LINE_WISE -> {
          startOffset =
            min(vimEditor.text().length, injector.motion.moveCaretToLineEnd(vimEditor, line, true) + 1)
          // At the end of a notebook cell the next symbol is a guard,
          // so we add a newline to be able to paste. Fixes VIM-2577
          if (startOffset > 0 && vimEditor.document.getOffsetGuard(Offset(startOffset)) != null) {
            application.runWriteAction { (vimEditor as MutableVimEditor).insertText((startOffset - 1).offset, "\n") }
          }
          if (startOffset > 0 && startOffset == vimEditor.text().length && vimEditor.text()[startOffset - 1] != '\n') {
            application.runWriteAction { (vimEditor as MutableVimEditor).insertText(startOffset.offset, "\n") }
            startOffset++
          }
        }
        else -> {
          startOffset = vimCaret.offset.point
          if (!vimEditor.isLineEmpty(line, false)) {
            startOffset++
          }
        }
      }

      return if (startOffset > vimEditor.text().length) listOf(vimEditor.text().length) else listOf(startOffset)
    }
  }

  @RWLockLabel.SelfSynchronized
  protected open fun putForCaret(
    editor: VimEditor,
    caret: VimCaret,
    visualSelection: VisualSelection?,
    pasteOptions: PasteOptions,
    indent: Boolean,
    additionalData: Map<String, Any>,
    context: ExecutionContext,
    text: TextData,
  ): Pair<VimCaret, RangeMarker>? {
    var updated = caret
    if (visualSelection?.typeInEditor?.isLine == true && editor.isOneLineMode()) return null
    val startOffsets = prepareDocumentAndGetStartOffsets(editor, updated, text.typeInRegister, visualSelection, pasteOptions, additionalData)

    // todo make block carets more convenient
    // todo visualSelection is obsolete here (text was deleted)
    val pasteStartOffset = startOffsets.minOrNull()!!
    var pasteEndOffset: Int? = null

    startOffsets.forEach { startOffset ->
      val subMode = visualSelection?.typeInEditor?.toSubMode() ?: VimStateMachine.SubMode.NONE
      val (endOffset, updatedCaret) = putTextInternal(
        editor, updated, context, text.text, text.typeInRegister, subMode,
        startOffset, pasteOptions.count, indent
      )
      if (startOffset == pasteStartOffset) {
        pasteEndOffset = endOffset
      }
      updated = updatedCaret
    }

    // It's not a bug. Vim literally assumes that only one line was changed
    injector.markService.setChangeMarks(updated, TextRange(pasteStartOffset, pasteEndOffset!!))
    return Pair(caret, createRangeMarker(editor, pasteStartOffset, pasteEndOffset!!))
  }

  override fun putTextForCaretNonVisual(
    caret: VimCaret,
    context: ExecutionContext,
    textData: TextData,
    pasteOptions: PasteOptions,
  ): RangeMarker? {
    assert(!caret.editor.inVisualMode)
    return putTextForCaret(
      caret,
      context,
      textData,
      null,
      pasteOptions,
      updateVisualMarks = false,
      modifyRegister = false
    )
  }

  @RWLockLabel.SelfSynchronized
  override fun putTextForCaret(
    caret: VimCaret,
    context: ExecutionContext,
    textData: TextData,
    visualSelection: VisualSelection?,
    pasteOptions: PasteOptions,
    updateVisualMarks: Boolean,
    modifyRegister: Boolean,
  ): RangeMarker? {
    val editor = caret.editor
    val indent = pasteOptions.shouldAddIndent(textData, visualSelection)
    val additionalData = collectPreModificationData(editor, visualSelection)
    visualSelection?.let {
      deleteSelectedText(
        editor,
        visualSelection,
        OperatorArguments(false, 0, editor.mode, editor.subMode),
        modifyRegister,
      )
    }
    val processedText = processText(caret, visualSelection, textData) ?: return null
   
    val (updatedCaret, rangeMarker) = putForCaret(editor, caret, visualSelection, pasteOptions, indent, additionalData, context, processedText) ?: return null
    if (updateVisualMarks) {
      // todo pass there rangeMarker?
      wrapInsertedTextWithVisualMarks(updatedCaret, visualSelection, processedText)
    }
    return rangeMarker
  }

  @RWLockLabel.SelfSynchronized
  public abstract fun putTextViaIde(
    vimEditor: VimEditor,
    vimContext: ExecutionContext,
    text: TextData,
    visualSelection: VisualSelection?,
    insertTextBeforeCaret: Boolean,
    caretAfterInsertedText: Boolean,
    indent: Boolean,
    putToLine: Int,
    count: Int,
    subMode: VimStateMachine.SubMode,
    additionalData: Map<String, Any>,
  ): Map<VimCaret, RangeMarker>?

  public companion object {
    public val logger: VimLogger by lazy { vimLogger<VimPutBase>() }
  }

  protected abstract fun createRangeMarker(editor: VimEditor, startOffset: Int, endOffset: Int): RangeMarker
}

// This is taken from StringUtil of IntelliJ IDEA
private fun CharSequence.getLineBreakCount(): Int {
  var count = 0
  var i = 0
  while (i < length) {
    val c = this[i]
    if (c == '\n') {
      count++
    } else if (c == '\r') {
      if (i + 1 < length && this[i + 1] == '\n') {
        i++
      }
      count++
    }
    i++
  }
  return count
}

