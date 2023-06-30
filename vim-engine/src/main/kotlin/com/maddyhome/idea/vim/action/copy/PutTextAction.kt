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
import com.maddyhome.idea.vim.handler.ChangeEditorActionHandler
import com.maddyhome.idea.vim.put.TextData

public sealed class PutTextBaseAction(
  private val insertTextBeforeCaret: Boolean,
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
    // todo this if is not needed
    return if (sortedCarets.size > 1) {
      val caretToTextData = sortedCarets.associateWith { getTextDataForCaret(it) }
      var result = true
      injector.application.runWriteAction {
        caretToTextData.forEach {
          result = injector.put.putTextForCaret(
            it.key,
            context,
            it.value,
            null,
            insertTextBeforeCaret,
            caretAfterInsertedText,
            indent,
            count,
            putToLine = -1,
            updateVisualMarks = false, // doesn't matter
            modifyRegister = false, // doesn't matter
            ) != null && result
        }
      }
      result
    } else {
      val textData = getTextDataForCaret(sortedCarets.single())
      injector.put.putText(
        editor,
        context,
        textData,
        null,
        insertTextBeforeCaret,
        caretAfterInsertedText,
        indent,
        operatorArguments,
        count,
        putToLine = -1,
        updateVisualMarks = false, // any (normal mode)
        modifyRegister = true, // any (normal mode)
        ) != null
    }
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
        register.text ?: injector.parser.toPrintableString(register.keys),
        register.type,
        register.transferableData,
        register.name,
      )
    }
    return textData
  }
}

public class PutTextAfterCursorAction : PutTextBaseAction(insertTextBeforeCaret = false, indent = true, caretAfterInsertedText = false)
public class PutTextAfterCursorActionMoveCursor : PutTextBaseAction(insertTextBeforeCaret = false, indent = true, caretAfterInsertedText = true)

public class PutTextAfterCursorNoIndentAction : PutTextBaseAction(insertTextBeforeCaret = false, indent = false, caretAfterInsertedText = false)
public class PutTextBeforeCursorNoIndentAction : PutTextBaseAction(insertTextBeforeCaret = true, indent = false, caretAfterInsertedText = false)

public class PutTextBeforeCursorAction : PutTextBaseAction(insertTextBeforeCaret = true, indent = true, caretAfterInsertedText = false)
public class PutTextBeforeCursorActionMoveCursor : PutTextBaseAction(insertTextBeforeCaret = true, indent = true, caretAfterInsertedText = true)
