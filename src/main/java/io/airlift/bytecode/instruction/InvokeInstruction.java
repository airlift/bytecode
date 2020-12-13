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
package io.airlift.bytecode.instruction;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import io.airlift.bytecode.BytecodeNode;
import io.airlift.bytecode.BytecodeVisitor;
import io.airlift.bytecode.MethodDefinition;
import io.airlift.bytecode.MethodGenerationContext;
import io.airlift.bytecode.OpCode;
import io.airlift.bytecode.ParameterizedType;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.transform;
import static io.airlift.bytecode.MethodDefinition.methodDescription;
import static io.airlift.bytecode.OpCode.INVOKEDYNAMIC;
import static io.airlift.bytecode.OpCode.INVOKEINTERFACE;
import static io.airlift.bytecode.OpCode.INVOKESPECIAL;
import static io.airlift.bytecode.OpCode.INVOKESTATIC;
import static io.airlift.bytecode.OpCode.INVOKEVIRTUAL;
import static io.airlift.bytecode.ParameterizedType.type;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("UnusedDeclaration")
public class InvokeInstruction
        implements InstructionNode
{
    //
    // Invoke Static
    //

    public static InstructionNode invokeStatic(Method method)
    {
        return invoke(INVOKESTATIC, method);
    }

    public static InstructionNode invokeStatic(MethodDefinition method)
    {
        return invoke(INVOKESTATIC, method);
    }

    public static InstructionNode invokeStatic(Class<?> target, String name, Class<?> returnType, Class<?>... parameterTypes)
    {
        return invoke(INVOKESTATIC, target, name, returnType, ImmutableList.copyOf(parameterTypes));
    }

    public static InstructionNode invokeStatic(Class<?> target, String name, Class<?> returnType, Iterable<Class<?>> parameterTypes)
    {
        return invoke(INVOKESTATIC, target, name, returnType, parameterTypes);
    }

    public static InstructionNode invokeStatic(ParameterizedType target, String name, ParameterizedType returnType, ParameterizedType... parameterTypes)
    {
        return invoke(INVOKESTATIC, target, name, returnType, ImmutableList.copyOf(parameterTypes));
    }

    public static InstructionNode invokeStatic(ParameterizedType target, String name, ParameterizedType returnType, Iterable<ParameterizedType> parameterTypes)
    {
        return invoke(INVOKESTATIC, target, name, returnType, parameterTypes);
    }

    //
    // Invoke Virtual
    //

    public static InstructionNode invokeVirtual(Method method)
    {
        return invoke(INVOKEVIRTUAL, method);
    }

    public static InstructionNode invokeVirtual(MethodDefinition method)
    {
        return invoke(INVOKEVIRTUAL, method);
    }

    public static InstructionNode invokeVirtual(Class<?> target, String name, Class<?> returnType, Class<?>... parameterTypes)
    {
        return invoke(INVOKEVIRTUAL, target, name, returnType, ImmutableList.copyOf(parameterTypes));
    }

    public static InstructionNode invokeVirtual(Class<?> target, String name, Class<?> returnType, Iterable<Class<?>> parameterTypes)
    {
        return invoke(INVOKEVIRTUAL, target, name, returnType, parameterTypes);
    }

    public static InstructionNode invokeVirtual(ParameterizedType target, String name, ParameterizedType returnType, ParameterizedType... parameterTypes)
    {
        return invoke(INVOKEVIRTUAL, target, name, returnType, ImmutableList.copyOf(parameterTypes));
    }

    public static InstructionNode invokeVirtual(ParameterizedType target, String name, ParameterizedType returnType, Iterable<ParameterizedType> parameterTypes)
    {
        return invoke(INVOKEVIRTUAL, target, name, returnType, parameterTypes);
    }

    //
    // Invoke Interface
    //

    public static InstructionNode invokeInterface(Method method)
    {
        return invoke(INVOKEINTERFACE, method);
    }

    public static InstructionNode invokeInterface(MethodDefinition method)
    {
        return invoke(INVOKEINTERFACE, method);
    }

    public static InstructionNode invokeInterface(Class<?> target, String name, Class<?> returnType, Class<?>... parameterTypes)
    {
        return invoke(INVOKEINTERFACE, target, name, returnType, ImmutableList.copyOf(parameterTypes));
    }

    public static InstructionNode invokeInterface(Class<?> target, String name, Class<?> returnType, Iterable<Class<?>> parameterTypes)
    {
        return invoke(INVOKEINTERFACE, target, name, returnType, parameterTypes);
    }

    public static InstructionNode invokeInterface(ParameterizedType target, String name, ParameterizedType returnType, ParameterizedType... parameterTypes)
    {
        return invoke(INVOKEINTERFACE, target, name, returnType, ImmutableList.copyOf(parameterTypes));
    }

    public static InstructionNode invokeInterface(ParameterizedType target, String name, ParameterizedType returnType, Iterable<ParameterizedType> parameterTypes)
    {
        return invoke(INVOKEINTERFACE, target, name, returnType, parameterTypes);
    }

    //
    // Invoke Constructor
    //

    public static InstructionNode invokeConstructor(Constructor<?> constructor)
    {
        return invokeConstructor(constructor.getDeclaringClass(), constructor.getParameterTypes());
    }

    public static InstructionNode invokeConstructor(Class<?> target, Class<?>... parameterTypes)
    {
        return invokeConstructor(type(target), transform(ImmutableList.copyOf(parameterTypes), ParameterizedType::type));
    }

    public static InstructionNode invokeConstructor(Class<?> target, Iterable<Class<?>> parameterTypes)
    {
        return invokeConstructor(type(target), transform(parameterTypes, ParameterizedType::type));
    }

    public static InstructionNode invokeConstructor(ParameterizedType target, ParameterizedType... parameterTypes)
    {
        return invokeConstructor(target, ImmutableList.copyOf(parameterTypes));
    }

    public static InstructionNode invokeConstructor(ParameterizedType target, Iterable<ParameterizedType> parameterTypes)
    {
        return invokeSpecial(target, "<init>", type(void.class), parameterTypes);
    }

    //
    // Invoke Special
    //

    public static InstructionNode invokeSpecial(Method method)
    {
        return invoke(INVOKESPECIAL, method);
    }

    public static InstructionNode invokeSpecial(MethodDefinition method)
    {
        return invoke(INVOKESPECIAL, method);
    }

    public static InstructionNode invokeSpecial(Class<?> target, String name, Class<?> returnType, Class<?>... parameterTypes)
    {
        return invoke(INVOKESPECIAL, target, name, returnType, ImmutableList.copyOf(parameterTypes));
    }

    public static InstructionNode invokeSpecial(Class<?> target, String name, Class<?> returnType, Iterable<Class<?>> parameterTypes)
    {
        return invoke(INVOKESPECIAL, target, name, returnType, parameterTypes);
    }

    public static InstructionNode invokeSpecial(ParameterizedType target, String name, ParameterizedType returnType, ParameterizedType... parameterTypes)
    {
        return invoke(INVOKESPECIAL, target, name, returnType, ImmutableList.copyOf(parameterTypes));
    }

    public static InstructionNode invokeSpecial(ParameterizedType target, String name, ParameterizedType returnType, Iterable<ParameterizedType> parameterTypes)
    {
        return invoke(INVOKESPECIAL, target, name, returnType, parameterTypes);
    }

    //
    // Generic
    //

    private static InstructionNode invoke(OpCode invocationType, Method method)
    {
        return new InvokeInstruction(invocationType,
                type(method.getDeclaringClass()),
                method.getName(),
                type(method.getReturnType()),
                transform(ImmutableList.copyOf(method.getParameterTypes()), ParameterizedType::type));
    }

    private static InstructionNode invoke(OpCode invocationType, MethodDefinition method)
    {
        return new InvokeInstruction(invocationType,
                method.getDeclaringClass().getType(),
                method.getName(),
                method.getReturnType(),
                method.getParameterTypes());
    }

    private static InstructionNode invoke(OpCode invocationType, ParameterizedType target, String name, ParameterizedType returnType, Iterable<ParameterizedType> parameterTypes)
    {
        return new InvokeInstruction(invocationType,
                target,
                name,
                returnType,
                parameterTypes);
    }

    private static InstructionNode invoke(OpCode invocationType, Class<?> target, String name, Class<?> returnType, Iterable<Class<?>> parameterTypes)
    {
        return new InvokeInstruction(invocationType,
                type(target),
                name,
                type(returnType),
                transform(parameterTypes, ParameterizedType::type));
    }

    //
    // Invoke Dynamic
    //

    public static InstructionNode invokeDynamic(String name,
            ParameterizedType returnType,
            Iterable<ParameterizedType> parameterTypes,
            Method bootstrapMethod,
            Iterable<Object> bootstrapArguments)
    {
        return new InvokeDynamicInstruction(name,
                returnType,
                parameterTypes,
                BootstrapMethod.from(bootstrapMethod),
                ImmutableList.copyOf(bootstrapArguments));
    }

    public static InstructionNode invokeDynamic(String name,
            ParameterizedType returnType,
            Iterable<ParameterizedType> parameterTypes,
            Method bootstrapMethod,
            Object... bootstrapArguments)
    {
        return new InvokeDynamicInstruction(name,
                returnType,
                parameterTypes,
                BootstrapMethod.from(bootstrapMethod),
                ImmutableList.copyOf(bootstrapArguments));
    }

    public static InstructionNode invokeDynamic(String name,
            MethodType methodType,
            Method bootstrapMethod,
            Iterable<Object> bootstrapArguments)
    {
        return new InvokeDynamicInstruction(name,
                type(methodType.returnType()),
                transform(methodType.parameterList(), ParameterizedType::type),
                BootstrapMethod.from(bootstrapMethod),
                ImmutableList.copyOf(bootstrapArguments));
    }

    public static InstructionNode invokeDynamic(String name,
            MethodType methodType,
            Method bootstrapMethod,
            Object... bootstrapArguments)
    {
        return new InvokeDynamicInstruction(name,
                type(methodType.returnType()),
                transform(methodType.parameterList(), ParameterizedType::type),
                BootstrapMethod.from(bootstrapMethod),
                ImmutableList.copyOf(bootstrapArguments));
    }

    public static InstructionNode invokeDynamic(String name,
            ParameterizedType returnType,
            Iterable<ParameterizedType> parameterTypes,
            BootstrapMethod bootstrapMethod,
            Iterable<Object> bootstrapArguments)
    {
        return new InvokeDynamicInstruction(name,
                returnType,
                parameterTypes,
                bootstrapMethod,
                ImmutableList.copyOf(bootstrapArguments));
    }

    private final OpCode opCode;
    private final ParameterizedType target;
    private final String name;
    private final ParameterizedType returnType;
    private final List<ParameterizedType> parameterTypes;

    public InvokeInstruction(OpCode opCode,
            ParameterizedType target,
            String name,
            ParameterizedType returnType,
            Iterable<ParameterizedType> parameterTypes)
    {
        checkUnqualifiedName(name);
        this.opCode = opCode;
        this.target = target;
        this.name = name;
        this.returnType = returnType;
        this.parameterTypes = ImmutableList.copyOf(parameterTypes);
    }

    public OpCode getOpCode()
    {
        return opCode;
    }

    public ParameterizedType getTarget()
    {
        return target;
    }

    public String getName()
    {
        return name;
    }

    public ParameterizedType getReturnType()
    {
        return returnType;
    }

    public List<ParameterizedType> getParameterTypes()
    {
        return parameterTypes;
    }

    public String getMethodDescription()
    {
        return methodDescription(returnType, parameterTypes);
    }

    @Override
    public void accept(MethodVisitor visitor, MethodGenerationContext generationContext)
    {
        visitor.visitMethodInsn(opCode.getOpCode(), target.getClassName(), name, getMethodDescription(), target.isInterface());
    }

    @Override
    public List<BytecodeNode> getChildNodes()
    {
        return ImmutableList.of();
    }

    @Override
    public <T> T accept(BytecodeNode parent, BytecodeVisitor<T> visitor)
    {
        return visitor.visitInvoke(parent, this);
    }

    public static class InvokeDynamicInstruction
            extends InvokeInstruction
    {
        private final BootstrapMethod bootstrapMethod;
        private final List<Object> bootstrapArguments;

        public InvokeDynamicInstruction(String name,
                ParameterizedType returnType,
                Iterable<ParameterizedType> parameterTypes,
                BootstrapMethod bootstrapMethod,
                List<Object> bootstrapArguments)
        {
            super(INVOKEDYNAMIC, null, name, returnType, parameterTypes);
            this.bootstrapMethod = bootstrapMethod;
            this.bootstrapArguments = ImmutableList.copyOf(bootstrapArguments);
        }

        @Override
        public void accept(MethodVisitor visitor, MethodGenerationContext generationContext)
        {
            Handle bootstrapMethodHandle = new Handle(Opcodes.H_INVOKESTATIC,
                    bootstrapMethod.getOwnerClass().getClassName(),
                    bootstrapMethod.getName(),
                    methodDescription(bootstrapMethod.getReturnType(), bootstrapMethod.getParameterType()),
                    false);

            visitor.visitInvokeDynamicInsn(getName(),
                    getMethodDescription(),
                    bootstrapMethodHandle,
                    bootstrapArguments.toArray(new Object[bootstrapArguments.size()]));
        }

        public BootstrapMethod getBootstrapMethod()
        {
            return bootstrapMethod;
        }

        public List<Object> getBootstrapArguments()
        {
            return bootstrapArguments;
        }

        @Override
        public List<BytecodeNode> getChildNodes()
        {
            return ImmutableList.of();
        }

        @Override
        public <T> T accept(BytecodeNode parent, BytecodeVisitor<T> visitor)
        {
            return visitor.visitInvokeDynamic(parent, this);
        }
    }

    private static void checkUnqualifiedName(String name)
    {
        // JVM Specification 4.2.2 Unqualified Names
        requireNonNull(name, "name is null");
        checkArgument(!name.isEmpty(), "name is empty");
        if (name.equals("<init>") || name.equals("<clinit>")) {
            return;
        }
        CharMatcher invalid = CharMatcher.anyOf(".;[/<>");
        checkArgument(invalid.matchesNoneOf(name), "invalid name: %s", name);
    }

    public static class BootstrapMethod
    {
        private final ParameterizedType ownerClass;
        private final String name;
        private final ParameterizedType returnType;
        private final List<ParameterizedType> parameterType;

        public BootstrapMethod(ParameterizedType ownerClass, String name, ParameterizedType returnType, List<ParameterizedType> parameterType)
        {
            this.ownerClass = requireNonNull(ownerClass, "ownerClass is null");
            this.name = requireNonNull(name, "name is null");
            this.returnType = requireNonNull(returnType, "returnType is null");
            this.parameterType = ImmutableList.copyOf(requireNonNull(parameterType, "parameterType is null"));
        }

        public ParameterizedType getOwnerClass()
        {
            return ownerClass;
        }

        public String getName()
        {
            return name;
        }

        public ParameterizedType getReturnType()
        {
            return returnType;
        }

        public List<ParameterizedType> getParameterType()
        {
            return parameterType;
        }

        public static BootstrapMethod from(Method method)
        {
            return new BootstrapMethod(
                    type(method.getDeclaringClass()),
                    method.getName(),
                    type(method.getReturnType()),
                    stream(method.getParameterTypes())
                            .map(ParameterizedType::type)
                            .collect(toImmutableList()));
        }
    }
}
