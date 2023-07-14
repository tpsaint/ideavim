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
import com.maddyhome.idea.vim.api.globalOptions
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
import com.maddyhome.idea.vim.group.visual.VimSelection
import com.maddyhome.idea.vim.helper.RWLockLabel
import com.maddyhome.idea.vim.helper.inVisualMode
import com.maddyhome.idea.vim.helper.mode
import com.maddyhome.idea.vim.helper.subMode
import com.maddyhome.idea.vim.mark.VimMarkConstants.MARK_CHANGE_POS
import com.maddyhome.idea.vim.options.OptionConstants
import java.lang.RuntimeException
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

public abstract class VimPutBase : VimPut {
  private fun wrapInsertedTextWithVisualMarks(caret: VimCaret, insertedRange: TextRange) {
    injector.markService.setVisualSelectionMarks(caret, insertedRange)
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

  private fun processText(visualSelection: VisualSelection?, textData: TextData?): TextData? {
    var text = textData?.text ?: run {
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

  public abstract fun doIndent(caret: VimCaret, context: ExecutionContext, startOffset: Int, endOffset: Int): Int

  private fun putTextCharacterwise(caret: VimCaret, context: ExecutionContext, text: String, startOffset: Int, count: Int, indent: Boolean): Int {
    val editor = caret.editor
    val insertedText = text.repeat(count)
    injector.application.runWriteAction { (editor as MutableVimEditor).insertText(startOffset.offset, insertedText) }

    val endOffset = if (indent) {
      doIndent(caret, context, startOffset, startOffset + insertedText.length)
    } else {
      startOffset + insertedText.length
    }
    return endOffset
  }

  private fun putTextLinewise(
    caret: VimCaret,
    context: ExecutionContext,
    text: String,
    startOffset: Int,
    count: Int,
    indent: Boolean,
  ): Int {
    val editor = caret.editor
    val overlappedCarets = ArrayList<VimCaret>(editor.carets().size)
    // todo can we have overlapped carets during paste at all? Shouldn't it be already handled?
    for (possiblyOverlappedCaret in editor.carets()) {
      if (possiblyOverlappedCaret.offset.point != startOffset || possiblyOverlappedCaret == caret) continue

      val updated = possiblyOverlappedCaret.moveToMotion(
        injector.motion.getHorizontalMotion(editor, possiblyOverlappedCaret, 1, true),
      )
      overlappedCarets.add(updated)
    }

    val endOffset = putTextCharacterwise(caret, context, text, startOffset, count, indent)

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
    caret: VimCaret,
    context: ExecutionContext,
    text: String,
    mode: VimStateMachine.SubMode,
    startOffset: Int,
    count: Int,
    indent: Boolean,
  ): Int {
    // todo simplify me
    val editor = caret.editor
    val startPosition = editor.offsetToBufferPosition(startOffset)
    val currentColumn = if (mode == VimStateMachine.SubMode.VISUAL_LINE) 0 else startPosition.column
    var currentLine = startPosition.line

    val tokenizer = StringTokenizer(text, "\n")
    val lineCount = tokenizer.countTokens()

    if (currentLine + lineCount >= editor.nativeLineCount()) {
      val limit = currentLine + lineCount - editor.nativeLineCount()
      for (i in 0 until limit) {
        injector.application.runWriteAction { (editor as MutableVimEditor).insertText(editor.fileSize().toInt().offset, "\n") }
      }
    }

    val maxLen = getMaxSegmentLength(text)
    var endOffset = startOffset
    var firstPasteEndOffset: Int? = null // for some reason, Vim sets change marks or moves caret as if only the first line of block selection was pasted

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
      val insertedText = origSegment + segment.repeat(count - 1)
      injector.application.runWriteAction { (editor as MutableVimEditor).insertText(insertOffset.offset, insertedText) }

      endOffset += insertedText.length

      if (mode == VimStateMachine.SubMode.VISUAL_LINE) {
        injector.application.runWriteAction { (editor as MutableVimEditor).insertText(endOffset.offset, "\n") }
        ++endOffset
      } else {
        if (pad.isNotEmpty()) {
          injector.application.runWriteAction { (editor as MutableVimEditor).insertText(insertOffset.offset, pad) }
          endOffset += pad.length
        }
      }

      if (currentLine == startPosition.line) {
        firstPasteEndOffset = endOffset
      }
      ++currentLine
    }
    val lineLengthBeforeIndent = editor.lineLength(startPosition.line)
    if (indent) doIndent(caret, context, startOffset, endOffset)
    val lineLengthAfterIndent = editor.lineLength(startPosition.line)
    val indentSize = lineLengthAfterIndent - lineLengthBeforeIndent
    return firstPasteEndOffset!! + indentSize
  }

  private fun putTextInternal(
    caret: VimCaret,
    context: ExecutionContext,
    text: String,
    type: SelectionType,
    mode: VimStateMachine.SubMode,
    startOffset: Int,
    count: Int,
    indent: Boolean,
  ): Int {
    return when (type) {
      SelectionType.CHARACTER_WISE -> putTextCharacterwise(
        caret,
        context,
        text,
        startOffset,
        count,
        indent,
      )
      SelectionType.LINE_WISE -> putTextLinewise(
        caret,
        context,
        text,
        startOffset,
        count,
        indent,
      )
      else -> putTextBlockwise(caret, context, text, mode, startOffset, count, indent)
    }
  }

  @RWLockLabel.SelfSynchronized
  protected fun prepareDocumentAndGetStartOffsets(
    vimEditor: VimEditor,
    vimCaret: ImmutableVimCaret,
    typeInRegister: SelectionType,
    pasteOptions: PasteOptions,
    preModificationData: PreModificationData,
  ): List<Int> {
    // todo refactor me
    val insertTextBeforeCaret = if (pasteOptions is AtCaretPasteOptions) pasteOptions.direction == Direction.BACKWARDS else false
    val putToLine = if (pasteOptions is ToLinePasteOptions) pasteOptions.line else -1
    
    val application = injector.application
    if (preModificationData.visualSelection != null) {
      return when {
        preModificationData.selectionType.isChar && typeInRegister.isLine -> {
          application.runWriteAction { (vimEditor as MutableVimEditor).insertText(vimCaret.offset, "\n") }
          listOf(vimCaret.offset.point + 1)
        }
        preModificationData.selectionType.isBlock -> {
          val maxSelectedLine = preModificationData.minLineInBlockSelection + preModificationData.selectedLines - 1
          val line = (if (insertTextBeforeCaret) preModificationData.minLineInBlockSelection else maxSelectedLine)
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
            SelectionType.CHARACTER_WISE -> (maxSelectedLine downTo preModificationData.minLineInBlockSelection)
              .map { vimEditor.bufferPositionToOffset(BufferPosition(it, preModificationData.minColumnInBlockSelection)) }
            SelectionType.BLOCK_WISE -> listOf(
              vimEditor.bufferPositionToOffset(
                BufferPosition(preModificationData.minLineInBlockSelection, preModificationData.minColumnInBlockSelection),
              ),
            )
          }
        }
        preModificationData.selectionType.isLine -> {
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
    caret: VimCaret,
    pasteOptions: PasteOptions,
    indent: Boolean,
    preModificationData: PreModificationData,
    context: ExecutionContext,
    text: TextData,
  ): Pair<VimCaret, RangeMarker>? {
    val editor = caret.editor
    if (preModificationData.selectionType.isLine && editor.isOneLineMode()) return null
    val startOffsets = prepareDocumentAndGetStartOffsets(editor, caret, text.typeInRegister, pasteOptions, preModificationData)

    // todo make block carets more convenient
    val pasteStartOffset = startOffsets.minOrNull()!!
    var pasteEndOffset: Int? = null

    startOffsets.forEach { startOffset ->
      val endOffset = putTextInternal(caret, context, text.text, text.typeInRegister, preModificationData.subMode, startOffset, pasteOptions.count, indent)
      if (startOffset == pasteStartOffset) {
        pasteEndOffset = endOffset
      }
    }
    return Pair(caret, createRangeMarker(editor, pasteStartOffset, pasteEndOffset!!))
  }

  override fun putTextForCaretNonVisual(caret: VimCaret, context: ExecutionContext, textData: TextData, pasteOptions: PasteOptions): RangeMarker? {
    assert(!caret.editor.inVisualMode)
    return putTextForCaret(caret, context, textData, null, pasteOptions, updateVisualMarks = false, modifyRegister = false)
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
    val preModificationData = PreModificationData(editor, visualSelection)

    if (injector.globalOptions().clipboard.contains(OptionConstants.clipboard_ideaput)) {
        val insertedRange = putTextViaIde(caret, context, textData, pasteOptions, preModificationData)
        if (insertedRange != null) return insertedRange
    }

    val indent = pasteOptions.shouldAddIndent(textData, visualSelection)
    visualSelection?.let {
      deleteSelectedText(editor, visualSelection, OperatorArguments(false, 0, editor.mode, editor.subMode), modifyRegister)
    }
    val processedText = processText(visualSelection, textData) ?: return null

    val (updatedCaret, rangeMarker) = putForCaret(caret, pasteOptions, indent, preModificationData, context, processedText) ?: return null
    if (updateVisualMarks) {
      wrapInsertedTextWithVisualMarks(updatedCaret, rangeMarker.range)
    }
    injector.markService.setChangeMarks(updatedCaret, rangeMarker.range)
    injector.markService.setMark(caret, MARK_CHANGE_POS, rangeMarker.range.startOffset)

    return rangeMarker
  }

  @RWLockLabel.SelfSynchronized
  protected abstract fun putTextViaIde(
    caret: VimCaret,
    context: ExecutionContext,
    text: TextData,
    pasteOptions: PasteOptions,
    preModificationData: PreModificationData,
  ): RangeMarker?

  public companion object {
    public val logger: VimLogger by lazy { vimLogger<VimPutBase>() }
  }

  protected abstract fun createRangeMarker(editor: VimEditor, startOffset: Int, endOffset: Int): RangeMarker

  protected class PreModificationData(public val editor: VimEditor, public val visualSelection: VisualSelection?) {
    // todo is it any different to Editor submode?
    public val subMode: VimStateMachine.SubMode = visualSelection?.typeInEditor?.toSubMode() ?: VimStateMachine.SubMode.NONE
    public val selectionType: SelectionType = visualSelection?.typeInEditor ?: SelectionType.fromSubMode(subMode)

    public fun getSelection(caret: VimCaret): VimSelection? {
      return visualSelection?.caretsAndSelections?.get(caret)
    }

    // TODO do we need this methods or Block selection should handle such stuff itself? (state collected per caret with VisualSelection before execution)
    private val blockSelection = visualSelection?.caretsAndSelections?.get(editor.primaryCaret())
    private val selectionStart = blockSelection?.let { editor.offsetToBufferPosition(blockSelection.vimStart) }
    private val selectionEnd = blockSelection?.let { editor.offsetToBufferPosition(blockSelection.vimEnd) }

    public val minColumnInBlockSelection: Int get() = if (subMode == VimStateMachine.SubMode.VISUAL_BLOCK) {
      min(selectionStart!!.column, selectionEnd!!.column)
    } else {
      throw RuntimeException("You can't use call this method without block selection")
    }
    public val selectedLines: Int get() = if (subMode == VimStateMachine.SubMode.VISUAL_BLOCK) {
      abs(selectionStart!!.line - selectionEnd!!.line) + 1
    } else {
      throw RuntimeException("You can't use call this method without block selection")
    }
    public val minLineInBlockSelection: Int get() = if (subMode == VimStateMachine.SubMode.VISUAL_BLOCK) {
      min(selectionStart!!.line, selectionEnd!!.line)
    } else {
      throw RuntimeException("You can't use call this method without block selection")
    }
  }
}
