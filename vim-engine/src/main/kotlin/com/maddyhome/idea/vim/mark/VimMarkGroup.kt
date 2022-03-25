package com.maddyhome.idea.vim.mark

import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.common.TextRange

interface VimMarkGroup {
  fun saveJumpLocation(editor: VimEditor)
  fun setChangeMarks(vimEditor: VimEditor, range: TextRange)
  fun addJump(editor: VimEditor, reset: Boolean)

  /**
   * Gets the requested mark for the editor
   *
   * @param editor The editor to get the mark for
   * @param ch     The desired mark
   * @return The requested mark if set, null if not set
   */
  fun getMark(editor: VimEditor, ch: Char): Mark?

  /**
   * Get the requested jump.
   *
   * @param count Postive for next jump (Ctrl-I), negative for previous jump (Ctrl-O).
   * @return The jump or null if out of range.
   */
  fun getJump(count: Int): Jump?
}
