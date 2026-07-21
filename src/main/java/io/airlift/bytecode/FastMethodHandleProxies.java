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
import io.airlift.bytecode.control.TryCatch;
import io.airlift.bytecode.control.TryCatch.CatchBlock;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.bytecode.Access.FINAL;
import static io.airlift.bytecode.Access.PUBLIC;
import static io.airlift.bytecode.Access.SYNTHETIC;
import static io.airlift.bytecode.Access.a;
import static io.airlift.bytecode.HiddenClassGenerator.hiddenClassGenerator;
import static io.airlift.bytecode.Parameter.arg;
import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.ParameterizedType.typeFromJavaClassName;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

public final class FastMethodHandleProxies
{
    private FastMethodHandleProxies() {}

    /**
     * Faster version of {@link MethodHandleProxies#asInterfaceInstance(Class, MethodHandle)}.
     *
     * @param <T> the desired type of the wrapper, a single-method interface
     * @param type a class object representing {@code T}
     * @param target the method handle to invoke from the wrapper
     * @return a correctly-typed wrapper for the given target
     */
    public static <T> T asInterfaceInstance(Class<T> type, MethodHandle target)
    {
        return asInterfaceInstance(type.getSimpleName(), type, target);
    }

    /**
     * Faster version of {@link MethodHandleProxies#asInterfaceInstance(Class, MethodHandle)}.
     *
     * @param <T> the desired type of the wrapper, a single-method interface
     * @param className the name of the generated class
     * @param type a class object representing {@code T}
     * @param target the method handle to invoke from the wrapper
     * @return a correctly-typed wrapper for the given target
     */
    public static <T> T asInterfaceInstance(String className, Class<T> type, MethodHandle target)
    {
        return asInterfaceInstance(lookup(), className, type, target);
    }

    /**
     * Faster version of {@link MethodHandleProxies#asInterfaceInstance(Class, MethodHandle)}.
     * <p>
     * The proxy is defined as a hidden class in the runtime package of the lookup class, so
     * {@code type} must be visible from the lookup class rather than from this library.
     *
     * @param <T> the desired type of the wrapper, a single-method interface
     * @param lookup the lookup used to define the proxy class
     * @param className the name of the generated class
     * @param type a class object representing {@code T}
     * @param target the method handle to invoke from the wrapper
     * @return a correctly-typed wrapper for the given target
     */
    public static <T> T asInterfaceInstance(Lookup lookup, String className, Class<T> type, MethodHandle target)
    {
        requireNonNull(lookup, "lookup is null");
        requireNonNull(className, "className is null");
        requireNonNull(target, "target is null");
        checkArgument(type.isInterface() && Modifier.isPublic(type.getModifiers()), "not a public interface: %s", type.getName());

        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL, SYNTHETIC),
                typeFromJavaClassName(hiddenClassName(lookup, className)),
                type(Object.class),
                type(type));

        classDefinition.declareDefaultConstructor(a(PUBLIC));

        Method method = getSingleAbstractMethod(type);
        Class<?>[] parameterTypes = method.getParameterTypes();
        MethodHandle adaptedTarget = target.asType(methodType(method.getReturnType(), parameterTypes));

        List<Parameter> parameters = IntStream.range(0, parameterTypes.length)
                .mapToObj(i -> arg("arg" + i, parameterTypes[i]))
                .collect(toImmutableList());

        MethodDefinition methodDefinition = classDefinition.declareMethod(
                a(PUBLIC),
                method.getName(),
                type(method.getReturnType()),
                parameters);

        // unchecked throwables and exceptions declared by the interface method propagate
        // as-is; anything else is wrapped, matching MethodHandleProxies
        ImmutableList.Builder<ParameterizedType> exceptionTypes = ImmutableList.builder();
        exceptionTypes.add(type(RuntimeException.class), type(Error.class));
        for (Class<?> exceptionType : method.getExceptionTypes()) {
            methodDefinition.addException(exceptionType.asSubclass(Throwable.class));
            exceptionTypes.add(type(exceptionType));
        }

        BytecodeNode throwUndeclared = new BytecodeBlock()
                .newObject(UndeclaredThrowableException.class)
                .append(OpCode.DUP_X1)
                .swap()
                .invokeConstructor(UndeclaredThrowableException.class, Throwable.class)
                .throwObject();

        ClassDataBinder binder = new ClassDataBinder();
        BytecodeNode invocation = binder.bindHandle(adaptedTarget)
                .invoke(ImmutableList.copyOf(parameters))
                .ret();

        methodDefinition.getBody().append(new TryCatch(invocation, ImmutableList.of(
                new CatchBlock(new BytecodeBlock().throwObject(), exceptionTypes.build()),
                new CatchBlock(throwUndeclared, ImmutableList.of()))));

        Class<? extends T> newClass = hiddenClassGenerator(lookup)
                .defineHiddenClass(classDefinition, type, binder);
        try {
            return newClass.getDeclaredConstructor().newInstance();
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A hidden class must be defined in the runtime package of its lookup class, so the
     * caller's name is used only as the stem. The JVM appends a unique suffix, which makes
     * the stem unique without a counter.
     */
    private static String hiddenClassName(Lookup lookup, String className)
    {
        String stem = className.replace('.', '$').replace('/', '$');
        String packageName = lookup.lookupClass().getPackageName();
        return packageName.isEmpty() ? stem : packageName + "." + stem;
    }

    private static <T> Method getSingleAbstractMethod(Class<T> type)
    {
        return stream(type.getMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getDeclaringClass() != Object.class)
                .filter(FastMethodHandleProxies::notJavaObjectMethod)
                .collect(onlyElement());
    }

    private static boolean notJavaObjectMethod(Method method)
    {
        return notMethodMatches(method, "toString", String.class) &&
                notMethodMatches(method, "hashCode", int.class) &&
                notMethodMatches(method, "equals", boolean.class, Object.class);
    }

    private static boolean notMethodMatches(Method method, String name, Class<?> returnType, Class<?>... parameterTypes)
    {
        return method.getParameterCount() != parameterTypes.length ||
                method.getReturnType() != returnType ||
                !name.equals(method.getName()) ||
                !Arrays.equals(method.getParameterTypes(), parameterTypes);
    }
}
