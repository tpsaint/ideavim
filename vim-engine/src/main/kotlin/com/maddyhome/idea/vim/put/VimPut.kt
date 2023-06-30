/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.put

import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimCaret
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.helper.RWLockLabel

public interface VimPut {
  @RWLockLabel.SelfSynchronized
  public fun putText(
    editor: VimEditor,
    context: ExecutionContext,
    data: PutData,
    operatorArguments: OperatorArguments,
    updateVisualMarks: Boolean = false,
    modifyRegister: Boolean = true,
  ): Boolean

  @RWLockLabel.SelfSynchronized
  public fun putTextForCaret(editor: VimEditor, caret: VimCaret, context: ExecutionContext, data: PutData, updateVisualMarks: Boolean = false, modifyRegister: Boolean = true): Boolean
}
