/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.put

import com.maddyhome.idea.vim.command.SelectionType
import com.maddyhome.idea.vim.common.Direction

// TODO current [adjustIndent] is a huge lie. Review each constructor invocation.
/**
 * Provides basic information about where and how the text should be pasted.
 * @param adjustIndent if true, adjusts the indent to the current line.
 * See `:h ]p`.
 * @property count number of times the paste operation should be executed
 */
public sealed class PasteOptions(private val adjustIndent: Boolean, public val count: Int) {
  // Todo is this method really needed?.. And is the `||` a clean thing or something that just "can do both"?
  public fun shouldAddIndent(textData: TextData?, visualSelection: VisualSelection?): Boolean {
    return adjustIndent && (textData?.typeInRegister == SelectionType.LINE_WISE || visualSelection?.typeInEditor == SelectionType.LINE_WISE)
  }
}

/**
 * Suitable for most cases. Paste location is based on caret position.
 * @property direction specifies the direction of the paste operation.
 * @property adjustIndent if true, adjusts the indent to the current line.
 * See `:h ]p`.
 * @property count number of times the paste operation should be executed.
 */
public class AtCaretPasteOptions(public val direction: Direction, adjustIndent: Boolean = true, count: Int = 1): PasteOptions(adjustIndent, count)

/**
 * Should be used for ex-commands. Puts text to a specific line and does not take caret position into account.
 * @property line the line number where the text should be pasted.
 * @property adjustIndent if true, adjusts the indent to the current line.
 * See `:h ]p`.
 * @property count number of times the paste operation should be executed.
 */
public class ToLinePasteOptions(public val line: Int, adjustIndent: Boolean = true, count: Int = 1): PasteOptions(adjustIndent, count)