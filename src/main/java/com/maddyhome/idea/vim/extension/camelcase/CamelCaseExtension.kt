/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.extension.camelcase

import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.Argument
import com.maddyhome.idea.vim.command.Command
import com.maddyhome.idea.vim.command.CommandFlags
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.extension.ExtensionHandler
import com.maddyhome.idea.vim.extension.VimExtension
import com.maddyhome.idea.vim.extension.VimExtensionFacade
import com.maddyhome.idea.vim.extension.VimExtensionFacade.putKeyMappingIfMissing
import com.maddyhome.idea.vim.extension.camelcase.motions.CamelCaseMotionIB
import com.maddyhome.idea.vim.extension.camelcase.motions.CamelCaseMotionIE
import com.maddyhome.idea.vim.extension.camelcase.motions.CamelCaseMotionIW
import com.maddyhome.idea.vim.helper.vimStateMachine
import java.util.*

// todo make easier way to add motions
// todo why do we have the combination of VimExtensionFacade + putKeyMapping in init ?

// todo should motions return null or start/end offsets?
// todo what if caret is before/after word

// todo operator successfully processes count but visual don't
internal class CamelCaseExtension : VimExtension {
  override fun getName(): String = "camelcase"

  override fun init() {
    val keysIW = injector.parser.parseKeys("<Plug>CamelCaseMotion_iw")
    VimExtensionFacade.putExtensionHandlerMapping(MappingMode.O, keysIW, owner, OperatorIWMotionHandler(), false)
    VimExtensionFacade.putExtensionHandlerMapping(MappingMode.X, keysIW, owner, VisualIWMotionHandler(), false)
    putKeyMappingIfMissing(MappingMode.XO, injector.parser.parseKeys("i<leader>w"), owner, keysIW, true)
    
    val keysIB = injector.parser.parseKeys("<Plug>CamelCaseMotion_ib")
    VimExtensionFacade.putExtensionHandlerMapping(MappingMode.O, keysIB, owner, OperatorIBMotionHandler(), false)
    VimExtensionFacade.putExtensionHandlerMapping(MappingMode.X, keysIB, owner, VisualIBMotionHandler(), false)
    putKeyMappingIfMissing(MappingMode.XO, injector.parser.parseKeys("i<leader>b"), owner, keysIB, true)

    val keysIE = injector.parser.parseKeys("<Plug>CamelCaseMotion_ie")
    VimExtensionFacade.putExtensionHandlerMapping(MappingMode.O, keysIE, owner, OperatorIEMotionHandler(), false)
    VimExtensionFacade.putExtensionHandlerMapping(MappingMode.X, keysIE, owner, VisualIEMotionHandler(), false)
    putKeyMappingIfMissing(MappingMode.XO, injector.parser.parseKeys("i<leader>e"), owner, keysIE, true)
  }
  
  private class OperatorIWMotionHandler : ExtensionHandler {
    override val isRepeatable = true

    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      val commandState = editor.vimStateMachine
      val command = Command(operatorArguments.count1, CamelCaseMotionIW(), Command.Type.MOTION, EnumSet.noneOf(CommandFlags::class.java))
      commandState.commandBuilder.completeCommandPart(Argument(command))
    }
  }

  private class VisualIWMotionHandler : ExtensionHandler {
    override val isRepeatable = true

    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      CamelCaseMotionIW().execute(editor, injector.executionContextManager.onEditor(editor, context), operatorArguments)
    }
  }

  private class OperatorIBMotionHandler : ExtensionHandler {
    override val isRepeatable = true

    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      val commandState = editor.vimStateMachine
      val command = Command(operatorArguments.count1, CamelCaseMotionIB(), Command.Type.MOTION, EnumSet.noneOf(CommandFlags::class.java))
      commandState.commandBuilder.completeCommandPart(Argument(command))
    }
  }

  private class VisualIBMotionHandler : ExtensionHandler {
    override val isRepeatable = true

    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      CamelCaseMotionIB().execute(editor, injector.executionContextManager.onEditor(editor, context), operatorArguments)
    }
  }

  private class OperatorIEMotionHandler : ExtensionHandler {
    override val isRepeatable = true

    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      val commandState = editor.vimStateMachine
      val command = Command(operatorArguments.count1, CamelCaseMotionIE(), Command.Type.MOTION, EnumSet.noneOf(CommandFlags::class.java))
      commandState.commandBuilder.completeCommandPart(Argument(command))
    }
  }

  private class VisualIEMotionHandler : ExtensionHandler {
    override val isRepeatable = true

    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      CamelCaseMotionIE().execute(editor, injector.executionContextManager.onEditor(editor, context), operatorArguments)
    }
  }
}