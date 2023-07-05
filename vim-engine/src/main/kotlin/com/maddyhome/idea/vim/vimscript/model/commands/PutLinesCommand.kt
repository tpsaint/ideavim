/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.vimscript.model.commands

import com.intellij.vim.annotations.ExCommand
import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.command.SelectionType
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.ex.ranges.Ranges
import com.maddyhome.idea.vim.put.RangeMarker
import com.maddyhome.idea.vim.put.TextData
import com.maddyhome.idea.vim.put.ToLinePasteOptions
import com.maddyhome.idea.vim.vimscript.model.ExecutionResult

/**
 * see "h :put"
 */
@ExCommand(command = "pu[t]")
// TODO make it support multiple carets
public data class PutLinesCommand(val ranges: Ranges, val argument: String) : Command.SingleExecution(ranges, argument) {
  override val argFlags: CommandHandlerFlags = flags(RangeFlag.RANGE_OPTIONAL, ArgumentFlag.ARGUMENT_OPTIONAL, Access.READ_ONLY)

  override fun processCommand(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments): ExecutionResult {
    if (editor.isOneLineMode()) return ExecutionResult.Error

    val registerGroup = injector.registerGroup
    val arg = argument
    if (arg.isNotEmpty()) {
      if (!registerGroup.selectRegister(arg[0])) {
        return ExecutionResult.Error
      }
    } else {
      registerGroup.selectRegister(registerGroup.defaultRegister)
    }

    val line = if (ranges.size() == 0) -1 else getLine(editor)
    val textData = registerGroup.lastRegister?.let {
      TextData(
        it.text ?: injector.parser.toKeyNotation(it.keys),
        SelectionType.LINE_WISE,
        it.transferableData,
        null,
      )
    }
    try {
      editor.forEachCaret {
        injector.put.putTextForCaretNonVisual(
          editor.primaryCaret(),
          context,
          textData,
          ToLinePasteOptions(line, rawIndent = false),
          caretAfterInsertedText = false,
        ) ?: throw ExException("Failed to put line")
      }
    } catch (e: ExException) {
      return ExecutionResult.Error
    }
    return ExecutionResult.Success
  }
}
