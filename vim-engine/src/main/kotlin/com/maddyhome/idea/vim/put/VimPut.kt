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
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.helper.RWLockLabel

public interface VimPut {
  @RWLockLabel.SelfSynchronized
  public fun putText(
    editor: VimEditor,
    context: ExecutionContext,
    textData: TextData?,
    visualSelection: VisualSelection?,
    insertTextBeforeCaret: Boolean,
    caretAfterInsertedText: Boolean,
    rawIndent: Boolean,
    operatorArguments: OperatorArguments,
    count: Int,
    putToLine: Int = -1,
    updateVisualMarks: Boolean = false,
    modifyRegister: Boolean = true,
  ): Map<VimCaret, RangeMarker>?

  @RWLockLabel.SelfSynchronized
  public fun putTextForCaretNonVisual(
    caret: VimCaret,
    context: ExecutionContext,
    textData: TextData?,
    insertTextBeforeCaret: Boolean,
    caretAfterInsertedText: Boolean,
    rawIndent: Boolean,
    count: Int,
    putToLine: Int = -1,
  ): RangeMarker?

  @RWLockLabel.SelfSynchronized
  public fun putTextForCaret(
    caret: VimCaret,
    context: ExecutionContext,
    textData: TextData?,
    visualSelection: VisualSelection?,
    insertTextBeforeCaret: Boolean,
    caretAfterInsertedText: Boolean,
    rawIndent: Boolean,
    count: Int,
    putToLine: Int = -1,
    updateVisualMarks: Boolean,
    modifyRegister: Boolean,
  ): RangeMarker?
}

public interface RangeMarker {
  public val range: TextRange
}
