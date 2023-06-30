/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.action.copy

import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimCaret
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.Command
import com.maddyhome.idea.vim.command.CommandFlags
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.group.visual.VimSelection
import com.maddyhome.idea.vim.handler.VisualOperatorActionHandler
import com.maddyhome.idea.vim.helper.enumSetOf
import com.maddyhome.idea.vim.put.TextData
import com.maddyhome.idea.vim.put.VisualSelection
import java.util.*

/**
 * @author vlan
 */
public sealed class PutVisualTextBaseAction(
  private val insertTextBeforeCaret: Boolean,
  private val indent: Boolean,
  private val caretAfterInsertedText: Boolean,
  private val modifyRegister: Boolean = true,
) : VisualOperatorActionHandler.SingleExecution() {

  override val type: Command.Type = Command.Type.OTHER_SELF_SYNCHRONIZED

  override val flags: EnumSet<CommandFlags> = enumSetOf(CommandFlags.FLAG_EXIT_VISUAL)

  override fun executeForAllCarets(
    editor: VimEditor,
    context: ExecutionContext,
    cmd: Command,
    caretsAndSelections: Map<VimCaret, VimSelection>,
    operatorArguments: OperatorArguments,
  ): Boolean {
    if (caretsAndSelections.isEmpty()) return false
    val count = cmd.count
    val caretToPutData = editor.sortedCarets().associateWith { getPutDataForCaret(it, caretsAndSelections[it], count) }
    injector.registerGroup.resetRegister()
    var result = true
    injector.application.runWriteAction {
      caretToPutData.forEach {
        result = injector.put.putTextForCaret(
          editor,
          it.key,
          context,
          it.value.first,
          it.value.second,
          insertTextBeforeCaret,
          caretAfterInsertedText,
          indent,
          count,
          putToLine = -1,
          updateVisualMarks = true,
          modifyRegister = modifyRegister,
          ) != null && result
      }
    }
    return result
  }

  private fun getPutDataForCaret(caret: VimCaret, selection: VimSelection?, count: Int): Pair<TextData?, VisualSelection?> {
    val lastRegisterChar = injector.registerGroup.lastRegisterChar
    val register = caret.registerStorage.getRegister(lastRegisterChar)
    val textData = register?.let {
      TextData(
        register.text ?: injector.parser.toPrintableString(register.keys),
        register.type,
        register.transferableData,
        register.name,
      )
    }
    val visualSelection = selection?.let { VisualSelection(mapOf(caret to it), it.type) }
    return Pair(textData, visualSelection)
  }
}

public class PutVisualTextBeforeCursorAction : PutVisualTextBaseAction(insertTextBeforeCaret = true, indent = true, caretAfterInsertedText = false, modifyRegister = false)
public class PutVisualTextAfterCursorAction : PutVisualTextBaseAction(insertTextBeforeCaret = false, indent = true, caretAfterInsertedText = false)

public class PutVisualTextBeforeCursorNoIndentAction : PutVisualTextBaseAction(insertTextBeforeCaret = true, indent = false, caretAfterInsertedText = false)
public class PutVisualTextAfterCursorNoIndentAction : PutVisualTextBaseAction(insertTextBeforeCaret = false, indent = false, caretAfterInsertedText = false)

public class PutVisualTextBeforeCursorMoveCursorAction : PutVisualTextBaseAction(insertTextBeforeCaret = true, indent = true, caretAfterInsertedText = true)
public class PutVisualTextAfterCursorMoveCursorAction : PutVisualTextBaseAction(insertTextBeforeCaret = false, indent = true, caretAfterInsertedText = true)
