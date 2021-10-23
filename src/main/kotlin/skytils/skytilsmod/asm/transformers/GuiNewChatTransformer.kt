/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2021 Skytils
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package skytils.skytilsmod.asm.transformers

import dev.falsehonesty.asmhelper.dsl.instructions.InsnListBuilder
import dev.falsehonesty.asmhelper.dsl.instructions.JumpCondition
import dev.falsehonesty.asmhelper.dsl.modify
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import skytils.skytilsmod.utils.Utils

fun injectNullCheck() = modify("net.minecraft.client.gui.GuiNewChat") {
    classNode.methods.find {
        Utils.equalsOneOf(it.name, "refreshChat", "b") && it.desc == "()V"
    }?.apply {
        var labelNode: LabelNode? = null
        var incInsn: VarInsnNode? = null
        for (insn in instructions) {
            if (insn is LabelNode) {
                var prev = insn.previous
                var valid = true
                for (i in 1..5) {
                    if (prev == null || !valid) {
                        valid = false
                        break
                    }
                    valid = when (i) {
                        1 -> prev is VarInsnNode && prev.opcode == Opcodes.ISTORE
                        2 -> prev.opcode == Opcodes.ISUB
                        3 -> prev.opcode == Opcodes.ICONST_1
                        4 -> prev is MethodInsnNode && prev.owner == "java/util/List" && prev.name == "size"
                        5 -> prev is FieldInsnNode && prev.opcode == Opcodes.GETFIELD && Utils.equalsOneOf(
                            prev.owner,
                            "net/minecraft/client/gui/GuiNewChat",
                            "avt"
                        ) && Utils.equalsOneOf(prev.name, "chatLines", "h") && prev.desc == "Ljava/util/List;"
                        else -> throw NotImplementedError()
                    }
                    prev = prev.previous
                }
                if (valid) {
                    labelNode = insn
                    incInsn = insn.previous as VarInsnNode
                }
            }
            if (labelNode != null && incInsn != null && insn is VarInsnNode && insn.opcode == Opcodes.ASTORE) {
                val temp = insn.previous?.previous?.previous?.previous ?: continue
                if (temp.opcode == Opcodes.GETFIELD && temp is FieldInsnNode && Utils.equalsOneOf(
                        temp.owner,
                        "net/minecraft/client/gui/GuiNewChat",
                        "avt"
                    ) && Utils.equalsOneOf(temp.name, "chatLines", "h") && temp.desc == "Ljava/util/List;"
                ) {
                    instructions.insert(insn, InsnListBuilder(this).apply {
                        aload(insn.`var`)
                        ifClause(JumpCondition.NON_NULL) {
                            insn(IincInsnNode(incInsn.`var`, -1))
                            jump(JumpCondition.GOTO, labelNode)
                        }
                    }.build())
                }
            }
        }
    }
}