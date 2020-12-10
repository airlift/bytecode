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
import com.google.common.collect.ImmutableMap;
import io.airlift.bytecode.control.TryCatch;
import io.airlift.bytecode.control.TryCatch.CatchBlock;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.bytecode.Access.FINAL;
import static io.airlift.bytecode.Access.PUBLIC;
import static io.airlift.bytecode.Access.SYNTHETIC;
import static io.airlift.bytecode.Access.a;
import static io.airlift.bytecode.BytecodeUtils.uniqueClassName;
import static io.airlift.bytecode.ClassGenerator.classGenerator;
import static io.airlift.bytecode.FastMethodHandleProxies.Bootstrap.BOOTSTRAP_METHOD;
import static io.airlift.bytecode.Parameter.arg;
import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.ParameterizedType.typeFromJavaClassName;
import static io.airlift.bytecode.expression.BytecodeExpressions.invokeDynamic;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Arrays.stream;

public final class FastMethodHandleProxies
{
    private static final String BASE_PACKAGE = FastMethodHandleProxies.class.getPackage().getName() + ".proxy";

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
        String className = uniqueClassName(BASE_PACKAGE, type.getSimpleName()).getClassName();
        return asInterfaceInstance(className, type, target);
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
        checkArgument(type.isInterface() && Modifier.isPublic(type.getModifiers()), "not a public interface: %s", type.getName());

        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL, SYNTHETIC),
                typeFromJavaClassName(className),
                type(Object.class),
                type(type));

        classDefinition.declareDefaultConstructor(a(PUBLIC));

        Method method = getSingleAbstractMethod(type);
        target = target.asType(methodType(method.getReturnType(), method.getParameterTypes()));

        defineProxyMethod(classDefinition, method);

        // note this will not work if interface class is not visible from this class loader,
        // but we must use this class loader to ensure the bootstrap method is visible
        ClassLoader targetClassLoader = FastMethodHandleProxies.class.getClassLoader();
        DynamicClassLoader dynamicClassLoader = new DynamicClassLoader(targetClassLoader, ImmutableMap.of(0L, target));
        Class<? extends T> newClass = classGenerator(dynamicClassLoader).defineClass(classDefinition, type);
        try {
            return newClass.getDeclaredConstructor().newInstance();
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void defineProxyMethod(ClassDefinition classDefinition, Method target)
    {
        List<Parameter> parameters = new ArrayList<>();
        Class<?>[] parameterTypes = target.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            parameters.add(arg("arg" + i, parameterTypes[i]));
        }

        MethodDefinition method = classDefinition.declareMethod(
                a(PUBLIC),
                target.getName(),
                type(target.getReturnType()),
                parameters);

        BytecodeNode invocation = invokeDynamic(
                BOOTSTRAP_METHOD,
                ImmutableList.of(),
                target.getName(),
                target.getReturnType(),
                parameters)
                .ret();

        ImmutableList.Builder<ParameterizedType> exceptionTypes = ImmutableList.builder();
        exceptionTypes.add(type(RuntimeException.class), type(Error.class));
        for (Class<?> exceptionType : target.getExceptionTypes()) {
            method.addException(exceptionType.asSubclass(Throwable.class));
            exceptionTypes.add(type(exceptionType));
        }

        BytecodeNode throwUndeclared = new BytecodeBlock()
                .newObject(UndeclaredThrowableException.class)
                .append(OpCode.DUP_X1)
                .swap()
                .invokeConstructor(UndeclaredThrowableException.class, Throwable.class)
                .throwObject();

        invocation = new TryCatch(invocation, ImmutableList.of(
                new CatchBlock(new BytecodeBlock().throwObject(), exceptionTypes.build()),
                new CatchBlock(throwUndeclared, ImmutableList.of())));

        method.getBody().append(invocation);
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

    public static final class Bootstrap
    {
        public static final Method BOOTSTRAP_METHOD;

        static {
            try {
                BOOTSTRAP_METHOD = Bootstrap.class.getMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class);
            }
            catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }

        private Bootstrap() {}

        @SuppressWarnings("unused")
        public static CallSite bootstrap(MethodHandles.Lookup callerLookup, String name, MethodType type)
        {
            DynamicClassLoader classLoader = (DynamicClassLoader) callerLookup.lookupClass().getClassLoader();
            return new ConstantCallSite(classLoader.getCallSiteBindings().get(0L));
        }
    }
}
