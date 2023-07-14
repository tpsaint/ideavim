/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.group.copy

import com.intellij.codeInsight.editorActions.TextBlockTransferable
import com.intellij.ide.CopyPasteManagerEx
import com.intellij.ide.DataManager
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.PlatformUtils
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimCaret
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.getLineEndOffset
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.SelectionType
import com.maddyhome.idea.vim.command.isBlock
import com.maddyhome.idea.vim.command.isChar
import com.maddyhome.idea.vim.command.isLine
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.diagnostic.debug
import com.maddyhome.idea.vim.group.NotificationService
import com.maddyhome.idea.vim.helper.RWLockLabel
import com.maddyhome.idea.vim.newapi.IjVimEditor
import com.maddyhome.idea.vim.newapi.ij
import com.maddyhome.idea.vim.put.PasteOptions
import com.maddyhome.idea.vim.put.RangeMarker
import com.maddyhome.idea.vim.put.TextData
import com.maddyhome.idea.vim.put.VimPutBase
import com.maddyhome.idea.vim.put.VisualSelection
import java.awt.datatransfer.DataFlavor
import java.lang.Integer.max
import kotlin.math.min

internal class PutGroup : VimPutBase() {
  private fun getProviderForPasteViaIde(
    editor: VimEditor,
    typeInRegister: SelectionType,
    visualSelection: VisualSelection?,
  ): PasteProvider? {
    if (visualSelection != null && visualSelection.typeInEditor.isBlock) return null
    if (typeInRegister.isLine || typeInRegister.isChar) {
      val context = DataManager.getInstance().getDataContext(editor.ij.contentComponent)
      val provider = PlatformDataKeys.PASTE_PROVIDER.getData(context)
      if (provider != null && provider.isPasteEnabled(context)) return provider
    }
    return null
  }

  override fun putForCaret(
    caret: VimCaret,
    pasteOptions: PasteOptions,
    indent: Boolean,
    preModificationData: PreModificationData,
    context: ExecutionContext,
    text: TextData
  ): Pair<VimCaret, com.maddyhome.idea.vim.put.RangeMarker>? {
    val editor = caret.editor
    NotificationService.notifyAboutIdeaPut(editor)
    var result: Pair<VimCaret, com.maddyhome.idea.vim.put.RangeMarker>? = null
    runWriteAction {
      result = super.putForCaret(caret, pasteOptions, indent, preModificationData, context, text)
    }
    return result
  }

  @RWLockLabel.SelfSynchronized
  override fun putTextViaIde(
    caret: VimCaret,
    context: ExecutionContext,
    text: TextData,
    pasteOptions: PasteOptions,
    preModificationData: PreModificationData,
  ): RangeMarker? {
    if (pasteOptions.count > 1) return null // todo add support for count
    val pasteProvider = getProviderForPasteViaIde(caret.editor, text.typeInRegister, preModificationData.visualSelection) ?: return null

    val editor = caret.editor
    val ijEditor = editor.ij
    val ijContext = context.context as DataContext
    val startOffset = prepareDocumentAndGetStartOffsets(editor, caret, text.typeInRegister, pasteOptions, preModificationData)
    return null
//    val pasteProvider = getProviderForPasteViaIde(vimEditor, text.typeInRegister, visualSelection, count) ?: return null
//
//    val editor = (vimEditor as IjVimEditor).editor
//    val context = vimContext.context as DataContext
//    val carets: MutableMap<Caret, RangeMarker> = mutableMapOf()
//    EditorHelper.getOrderedCaretsList(editor).forEach { caret ->
//      // todo refactor me
//      val pasteOptions = if (putToLine == -1) {
//        AtCaretPasteOptions(if (insertTextBeforeCaret) Direction.BACKWARDS else Direction.FORWARDS, indent, count)
//      } else {
//        ToLinePasteOptions(putToLine, indent, count)
//
//
//      val startOffset =
//        prepareDocumentAndGetStartOffsets(
//          vimEditor,
//          IjVimCaret(caret),
//          text.typeInRegister,
//          pasteOptions,
//          additionalData,
//        ).first()
//      val pointMarker = editor.document.createRangeMarker(startOffset, startOffset)
//      caret.moveToInlayAwareOffset(startOffset)
//      carets[caret] = pointMarker
//    }
//
//    val registerChar = text.registerChar
//    if (registerChar != null && registerChar in RegisterConstants.CLIPBOARD_REGISTERS) {
//      pasteProvider.performPaste(context)
//    } else {
//      pasteKeepingClipboard(text) {
//        pasteProvider.performPaste(context)
//      }
//    }
//
//    val caretsToPastedText = mutableMapOf<VimCaret, com.maddyhome.idea.vim.put.RangeMarker>()
//    val lastPastedRegion = if (carets.size == 1) editor.getUserData(EditorEx.LAST_PASTED_REGION) else null
//    carets.forEach { (caret, point) ->
//      val startOffset = point.startOffset
//      point.dispose()
//      if (!caret.isValid) return@forEach
//
//      val caretPossibleEndOffset = lastPastedRegion?.endOffset ?: (startOffset + text.text.length)
//      val endOffset = if (indent) {
//        doIndent(
//          IjVimCaret(caret),
//          vimContext,
//          startOffset,
//          caretPossibleEndOffset,
//        )
//      } else {
//        caretPossibleEndOffset
//      }
//      val vimCaret = caret.vim
//      injector.markService.setChangeMarks(vimCaret, TextRange(startOffset, endOffset))
//      injector.markService.setMark(vimCaret, MARK_CHANGE_POS, startOffset)
//      caretsToPastedText[vimCaret] = createRangeMarker(vimEditor, startOffset, endOffset)
//    }
//    return caretsToPastedText
  }

  override fun createRangeMarker(editor: VimEditor, startOffset: Int, endOffset: Int): com.maddyhome.idea.vim.put.RangeMarker {
    return object : com.maddyhome.idea.vim.put.RangeMarker {
      val isReversed = startOffset > endOffset
      val ijRangeMarker = editor.ij.document.createRangeMarker(min(startOffset, endOffset), max(startOffset, endOffset))

      override val range: TextRange
        get() {
          val ijTextRange = ijRangeMarker.textRange
          return if (isReversed) {
            TextRange(ijTextRange.endOffset, ijTextRange.startOffset)
          } else {
            TextRange(ijTextRange.startOffset, ijTextRange.endOffset)
          }
        }
    }
  }

  /**
   * ideaput - option that enables "smartness" of the insert operation. For example, it automatically
   *   inserts import statements, or converts Java code to kotlin.
   * Unfortunately, at the moment, this functionality of "additional text processing" is bound to
   *   paste operation. So here we do the trick, in order to insert text from the register with all the
   *   brains from IJ, we put this text into the clipboard and perform a regular IJ paste.
   * In order to do this properly, after the paste, we should remove the clipboard text from
   *   the kill ring (stack of clipboard items)
   * So, generally this function should look like this:
   * ```
   * setClipboardText(text)
   * try {
   *   performPaste()
   * } finally {
   *   removeTextFromClipboard()
   * }
   * ```
   * And it was like this till some moment. However, if our text to paste matches the text that is already placed
   *   in the clipboard, instead of putting new text on top of stack, it merges the text into the last stack item.
   * So, all the other code in this function is created to detect such case and do not remove last clipboard item.
   */
  private fun pasteKeepingClipboard(text: TextData, doPaste: () -> Unit) {
    val allContentsBefore = CopyPasteManager.getInstance().allContents
    val sizeBeforeInsert = allContentsBefore.size
    val firstItemBefore = allContentsBefore.firstOrNull()
    logger.debug { "Transferable classes: ${text.transferableData.joinToString { it.javaClass.name }}" }
    val origContent: TextBlockTransferable = injector.clipboardManager.setClipboardText(
      text.text,
      transferableData = text.transferableData,
    ) as TextBlockTransferable
    val allContentsAfter = CopyPasteManager.getInstance().allContents
    val sizeAfterInsert = allContentsAfter.size
    try {
      doPaste()
    } finally {
      val textInClipboard = (firstItemBefore as? TextBlockTransferable)
        ?.getTransferData(DataFlavor.stringFlavor) as? String
      val textOnTop = textInClipboard != null && textInClipboard != text.text
      if (sizeBeforeInsert != sizeAfterInsert || textOnTop) {
        // Sometimes an inserted text replaces an existing one. E.g. on insert with + or * register
        (CopyPasteManager.getInstance() as? CopyPasteManagerEx)?.run { removeContent(origContent) }
      }
    }
  }

  override fun doIndent(
    caret: VimCaret,
    context: ExecutionContext,
    startOffset: Int,
    endOffset: Int,
  ): Int {
    // Temp fix for VIM-2808. Should be removed after rider will fix it's issues
    if (PlatformUtils.isRider()) return endOffset

    val editor = caret.editor
    val startLine = editor.offsetToBufferPosition(startOffset).line
    val endLine = editor.offsetToBufferPosition(endOffset - 1).line
    val startLineOffset = (editor as IjVimEditor).editor.document.getLineStartOffset(startLine)
    val endLineOffset = editor.editor.document.getLineEndOffset(endLine)

    VimPlugin.getChange().autoIndentRange(
      editor,
      caret,
      context,
      TextRange(startLineOffset, endLineOffset),
    )
    return editor.getLineEndOffset(endLine, true)
  }
}
