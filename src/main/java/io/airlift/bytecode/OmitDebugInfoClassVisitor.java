/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.ASM9;

class OmitDebugInfoClassVisitor
        extends ClassVisitor
{
    public OmitDebugInfoClassVisitor(ClassVisitor cv)
    {
        super(ASM9, cv);
    }

    @Override
    public void visitSource(String source, String debug) {}

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        return new OmitDebugInfoMethodVisitor(cv.visitMethod(access, name, desc, signature, exceptions));
    }

    private static class OmitDebugInfoMethodVisitor
            extends MethodVisitor
    {
        public OmitDebugInfoMethodVisitor(MethodVisitor mv)
        {
            super(ASM9, mv);
        }

        @Override
        public void visitLineNumber(int line, Label start) {}

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {}
    }
}
