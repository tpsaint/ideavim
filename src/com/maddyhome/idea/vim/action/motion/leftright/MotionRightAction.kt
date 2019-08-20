/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2019 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.action.motion.leftright

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.action.MotionEditorAction
import com.maddyhome.idea.vim.command.Argument
import com.maddyhome.idea.vim.command.CommandFlags
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.handler.MotionActionHandler
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.KeyStroke

class MotionRightAction : MotionEditorAction() {
  override val mappingModes: Set<MappingMode> = MappingMode.NVO

  override val keyStrokesSet: Set<List<KeyStroke>> = parseKeysSet("l")

  override val flags: EnumSet<CommandFlags> = EnumSet.of(CommandFlags.FLAG_MOT_EXCLUSIVE)

  override fun makeActionHandler(): MotionActionHandler = MotionRightActionHandler
}

class MotionRightInsertAction : MotionEditorAction() {
  override val mappingModes: Set<MappingMode> = MappingMode.I

  override val keyStrokesSet: Set<List<KeyStroke>> = setOf(
    listOf(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)),
    listOf(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0))
  )

  override fun makeActionHandler(): MotionActionHandler = MotionRightActionHandler
}

private object MotionRightActionHandler : MotionActionHandler.ForEachCaret() {
  override fun getOffset(editor: Editor,
                         caret: Caret,
                         context: DataContext,
                         count: Int,
                         rawCount: Int,
                         argument: Argument?): Int {
    return VimPlugin.getMotion().moveCaretHorizontal(editor, caret, count, true)
  }
}
