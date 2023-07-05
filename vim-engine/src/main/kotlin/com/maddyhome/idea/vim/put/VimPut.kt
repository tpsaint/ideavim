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
import com.maddyhome.idea.vim.command.SelectionType
import com.maddyhome.idea.vim.common.Direction
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.helper.RWLockLabel

public interface VimPut {
  @RWLockLabel.SelfSynchronized
  public fun putTextForCaretNonVisual(
    caret: VimCaret,
    context: ExecutionContext,
    textData: TextData?,
    pasteOptions: PasteOptions,
    caretAfterInsertedText: Boolean,
  ): RangeMarker?

  @RWLockLabel.SelfSynchronized
  public fun putTextForCaret(
    caret: VimCaret,
    context: ExecutionContext,
    textData: TextData?,
    visualSelection: VisualSelection?,
    pasteOptions: PasteOptions,
    caretAfterInsertedText: Boolean,
    updateVisualMarks: Boolean,
    modifyRegister: Boolean,
  ): RangeMarker?
}

public interface RangeMarker {
  public val range: TextRange
}

/**
 * @param adjustIndent - see :h ]p
 */
public sealed class PasteOptions(private val rawIndent: Boolean, public val count: Int) {
  public fun getIndent(textData: TextData?, visualSelection: VisualSelection?): Boolean {
    return if (rawIndent && textData?.typeInRegister != SelectionType.LINE_WISE && visualSelection?.typeInEditor != SelectionType.LINE_WISE) false else rawIndent
  }
}
public class AtCaretPasteOptions(public val direction: Direction, rawIndent: Boolean = true, count: Int = 1): PasteOptions(rawIndent, count)
public class ToLinePasteOptions(public val line: Int, rawIndent: Boolean = true, count: Int = 1): PasteOptions(rawIndent, count)
