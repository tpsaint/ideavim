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

package com.maddyhome.idea.vim.action.motion.search;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.action.MotionEditorAction;
import com.maddyhome.idea.vim.command.Argument;
import com.maddyhome.idea.vim.command.CommandFlags;
import com.maddyhome.idea.vim.command.MappingMode;
import com.maddyhome.idea.vim.handler.MotionActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class SearchAgainPreviousAction extends MotionEditorAction {
  @NotNull
  @Override
  public Set<MappingMode> getMappingModes() {
    return MappingMode.NVO;
  }

  @NotNull
  @Override
  public Set<List<KeyStroke>> getKeyStrokesSet() {
    return parseKeysSet("N");
  }

  @NotNull
  @Override
  public EnumSet<CommandFlags> getFlags() {
    return EnumSet.of(CommandFlags.FLAG_MOT_EXCLUSIVE, CommandFlags.FLAG_SAVE_JUMP);
  }

  @NotNull
  @Override
  public MotionActionHandler makeActionHandler() {
    return new MotionActionHandler.ForEachCaret() {
      @Override
      public int getOffset(@NotNull Editor editor,
                           @NotNull Caret caret,
                           @NotNull DataContext context,
                           int count,
                           int rawCount,
                           @Nullable Argument argument) {
        return VimPlugin.getSearch().searchPrevious(editor, caret, count);
      }
    };
  }
}
