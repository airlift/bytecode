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

import com.google.common.collect.ImmutableList;
import io.airlift.bytecode.expression.BytecodeExpression;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantClassDataAt;
import static java.util.Objects.requireNonNull;

/**
 * Collects runtime objects referenced by generated code and binds each one as
 * a class data constant of a hidden class. Each bound object is loaded with a
 * dynamic constant that is resolved once and then folded by the JIT.
 *
 * <pre>{@code
 * ClassDataBinder binder = new ClassDataBinder();
 *
 * method.getBody()
 *         .append(binder.bindHandle(methodHandle)
 *                 .invoke(argument)
 *                 .ret());
 *
 * hiddenClassGenerator(lookup)
 *         .defineHiddenClass(classDefinition, superType, binder);
 * }</pre>
 *
 * Bindings are deduplicated by identity: binding the same object again reuses
 * its class data slot, while objects that are merely equal stay separate.
 */
public final class ClassDataBinder
{
    private final List<Object> bindings = new ArrayList<>();
    private final Map<Object, Integer> bindingIndexes = new IdentityHashMap<>();

    public BytecodeExpression bind(Object constant, Class<?> type)
    {
        return bind(constant, type(type));
    }

    public BytecodeExpression bind(Object constant, ParameterizedType type)
    {
        requireNonNull(constant, "constant is null");
        requireNonNull(type, "type is null");

        Integer index = bindingIndexes.get(constant);
        if (index == null) {
            index = bindings.size();
            bindings.add(constant);
            bindingIndexes.put(constant, index);
        }
        return constantClassDataAt(index, type);
    }

    /**
     * Binds a method handle to be invoked exactly by the generated code.
     */
    public BoundMethodHandle bindHandle(MethodHandle handle)
    {
        requireNonNull(handle, "handle is null");
        return new BoundMethodHandle(bind(handle, MethodHandle.class), handle.type());
    }

    /**
     * The bound objects, to be passed as the class data when defining the hidden class.
     */
    public List<Object> getBindings()
    {
        return ImmutableList.copyOf(bindings);
    }

    /**
     * A method handle bound as a class data constant.
     */
    public static final class BoundMethodHandle
    {
        private final BytecodeExpression handle;
        private final MethodType type;

        private BoundMethodHandle(BytecodeExpression handle, MethodType type)
        {
            this.handle = handle;
            this.type = type;
        }

        public MethodType type()
        {
            return type;
        }

        /**
         * Loads the bound method handle, for callers that must place it on the stack
         * themselves, e.g. below arguments that are already being pushed.
         */
        public BytecodeExpression handle()
        {
            return handle;
        }

        public BytecodeExpression invoke(BytecodeExpression... arguments)
        {
            return invoke(ImmutableList.copyOf(arguments));
        }

        /**
         * Invokes the bound method handle exactly with the given arguments. The
         * argument types must match the handle type exactly, as the invocation
         * does not adapt arguments.
         */
        public BytecodeExpression invoke(List<BytecodeExpression> arguments)
        {
            checkArgument(arguments.size() == type.parameterCount(), "Expected %s arguments, but got %s", type.parameterCount(), arguments.size());
            for (int i = 0; i < arguments.size(); i++) {
                ParameterizedType expected = ParameterizedType.type(type.parameterType(i));
                ParameterizedType actual = arguments.get(i).getType();
                checkArgument(expected.equals(actual), "Expected argument %s to have type %s, but got %s", i, expected, actual);
            }
            return handle.invoke("invokeExact", type.returnType(), arguments);
        }
    }
}
