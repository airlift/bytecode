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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
 *         .append(binder.bind(methodHandle, MethodHandle.class)
 *                 .invoke("invokeExact", long.class, argument)
 *                 .ret());
 *
 * hiddenClassGenerator(lookup)
 *         .defineHiddenClass(classDefinition, superType, Optional.of(binder.getBindings()));
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
     * The bound objects, to be passed as the class data when defining the hidden class.
     */
    public List<Object> getBindings()
    {
        return ImmutableList.copyOf(bindings);
    }
}
