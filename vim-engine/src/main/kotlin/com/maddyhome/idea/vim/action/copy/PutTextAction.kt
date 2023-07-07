/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */
package com.maddyhome.idea.vim.action.copy

import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.ImmutableVimCaret
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.Argument
import com.maddyhome.idea.vim.command.Command
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.command.VimStateMachine
import com.maddyhome.idea.vim.common.Direction
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.handler.ChangeEditorActionHandler
import com.maddyhome.idea.vim.put.AtCaretPasteOptions
import com.maddyhome.idea.vim.put.TextData

public sealed class PutTextBaseAction(
  private val direction: Direction,
  private val indent: Boolean,
  private val caretAfterInsertedText: Boolean,
) : ChangeEditorActionHandler.SingleExecution() {
  override val type: Command.Type = Command.Type.OTHER_SELF_SYNCHRONIZED

  override fun execute(
    editor: VimEditor,
    context: ExecutionContext,
    argument: Argument?,
    operatorArguments: OperatorArguments,
  ): Boolean {
    val count = operatorArguments.count1
    val sortedCarets = editor.sortedCarets()
    val caretToTextData = sortedCarets.associateWith { getTextDataForCaret(it) }
    var result = true
    injector.application.runWriteAction {
      try {
        caretToTextData.forEach {
          if (it.value == null) throw ExException("Register is empty")
          val insertedRange = injector.put.putTextForCaretNonVisual(
            it.key,
            context,
            it.value!!,
            AtCaretPasteOptions(direction, indent, count),
          ) ?: throw ExException("Failed to perform paste")
          it.key.moveToTextRange(insertedRange.range, it.value?.typeInRegister!!, VimStateMachine.SubMode.NONE, if (caretAfterInsertedText) Direction.FORWARDS else Direction.BACKWARDS)
        }
      } catch (e: ExException) {
        result = false
      }
    }
    return result
  }

  private fun getTextDataForCaret(caret: ImmutableVimCaret): TextData? {
    val registerService = injector.registerGroup
    val registerChar = if (caret.editor.carets().size == 1) {
      registerService.currentRegister
    } else {
      registerService.getCurrentRegisterForMulticaret()
    }
    val register = caret.registerStorage.getRegister(registerChar)
    val textData = register?.let {
      TextData(
        register.text,
        register.type,
        register.transferableData,
        register.name,
      )
    }
    return textData
  }
}

public class PutTextAfterCursorAction : PutTextBaseAction(Direction.FORWARDS, indent = true, caretAfterInsertedText = false)
public class PutTextAfterCursorActionMoveCursor : PutTextBaseAction(Direction.FORWARDS, indent = true, caretAfterInsertedText = true)

public class PutTextAfterCursorNoIndentAction : PutTextBaseAction(Direction.FORWARDS, indent = false, caretAfterInsertedText = false)
public class PutTextBeforeCursorNoIndentAction : PutTextBaseAction(Direction.BACKWARDS, indent = false, caretAfterInsertedText = false)

public class PutTextBeforeCursorAction : PutTextBaseAction(Direction.BACKWARDS, indent = true, caretAfterInsertedText = false)
public class PutTextBeforeCursorActionMoveCursor : PutTextBaseAction(Direction.BACKWARDS, indent = true, caretAfterInsertedText = true)
