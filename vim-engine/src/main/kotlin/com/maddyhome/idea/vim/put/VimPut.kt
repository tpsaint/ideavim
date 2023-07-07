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
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.helper.RWLockLabel

public interface VimPut {
  @RWLockLabel.SelfSynchronized
  public fun putTextForCaretNonVisual(caret: VimCaret, context: ExecutionContext, textData: TextData, pasteOptions: PasteOptions): RangeMarker?

  // TODO when does it return null?
  /**
   * see ":h gv":
   * After using "p" or "P" in Visual mode the text that was put will be selected
   */
  @RWLockLabel.SelfSynchronized
  public fun putTextForCaret(caret: VimCaret, context: ExecutionContext, textData: TextData, visualSelection: VisualSelection?, pasteOptions: PasteOptions,
    updateVisualMarks: Boolean, modifyRegister: Boolean, ): RangeMarker?
}

// Todo should I return range markers at all?..
public interface RangeMarker {
  public val range: TextRange
}
