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
import com.maddyhome.idea.vim.command.SelectionType
import com.maddyhome.idea.vim.command.VimStateMachine
import com.maddyhome.idea.vim.common.Direction
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.group.visual.VimSelection
import com.maddyhome.idea.vim.handler.VisualOperatorActionHandler
import com.maddyhome.idea.vim.helper.enumSetOf
import com.maddyhome.idea.vim.put.AtCaretPasteOptions
import com.maddyhome.idea.vim.put.TextData
import com.maddyhome.idea.vim.put.VisualSelection
import java.util.*

/**
 * @author vlan
 */
public sealed class PutVisualTextBaseAction(
  private val direction: Direction,
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
      try {
        caretToPutData.forEach {
          val textData = it.value.first
          if (textData != null) {
            val insertedRange = injector.put.putTextForCaret(
              it.key,
              context,
              textData ?: TextData("", SelectionType.CHARACTER_WISE, emptyList(), null),
              it.value.second,
              AtCaretPasteOptions(direction, indent, count),
              updateVisualMarks = true,
              modifyRegister = modifyRegister,
            ) ?: throw ExException("Failed to perform paste")
            it.key.moveToTextRange(insertedRange.range, it.value.first!!.typeInRegister, it.value.second?.typeInEditor?.toSubMode() ?: VimStateMachine.SubMode.NONE, if (caretAfterInsertedText) Direction.FORWARDS else Direction.BACKWARDS)
          } else {
            val selection = caretsAndSelections[it.key]
            val textRange = selection?.toVimTextRange() ?: TextRange(it.key.offset.point, it.key.offset.point)
            injector.changeGroup.deleteRange(editor, it.key, textRange, selection?.type, true, operatorArguments, false)
            // TODO this exception should not be caught
            throw ExException("E353: Nothing in register ${injector.registerGroup.getCurrentRegisterForMulticaret()}")
          }
        }
      } catch (e: ExException) {
        result = false
      }
    }
    return result
  }

  private fun getPutDataForCaret(caret: VimCaret, selection: VimSelection?, count: Int): Pair<TextData?, VisualSelection?> {
    val lastRegisterChar = injector.registerGroup.getCurrentRegisterForMulticaret()
    val register = caret.registerStorage.getRegister(lastRegisterChar)
    val textData = register?.let {
      TextData(
        register.text,
        register.type,
        register.transferableData,
        register.name,
      )
    }
    val visualSelection = selection?.let { VisualSelection(mapOf(caret to it), it.type) }
    return Pair(textData, visualSelection)
  }
}

public class PutVisualTextBeforeCursorAction : PutVisualTextBaseAction(Direction.BACKWARDS, indent = true, caretAfterInsertedText = false, modifyRegister = false)
public class PutVisualTextAfterCursorAction : PutVisualTextBaseAction(Direction.FORWARDS, indent = true, caretAfterInsertedText = false)

public class PutVisualTextBeforeCursorNoIndentAction : PutVisualTextBaseAction(Direction.BACKWARDS, indent = false, caretAfterInsertedText = false)
public class PutVisualTextAfterCursorNoIndentAction : PutVisualTextBaseAction(Direction.FORWARDS, indent = false, caretAfterInsertedText = false)

public class PutVisualTextBeforeCursorMoveCursorAction : PutVisualTextBaseAction(Direction.BACKWARDS, indent = true, caretAfterInsertedText = true)
public class PutVisualTextAfterCursorMoveCursorAction : PutVisualTextBaseAction(Direction.FORWARDS, indent = true, caretAfterInsertedText = true)
