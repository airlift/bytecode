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

import com.google.common.base.VerifyException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.airlift.bytecode.FastMethodHandleProxies.getSingleAbstractMethod;
import static io.airlift.bytecode.FastMethodHandleProxies.toDirectMethodHandle;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;
import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testng.Assert.assertEquals;

public class TestFastMethodHandleProxies
{
    @Test
    public void testBasic()
            throws ReflectiveOperationException
    {
        assertInterface(
                LongUnaryOperator.class,
                lookup().findStatic(getClass(), "increment", methodType(long.class, long.class)),
                (addOne, wrapped) -> assertEquals(addOne.applyAsLong(1), 2L));
    }

    private static long increment(long x)
    {
        return x + 1;
    }

    @Test
    public void testGeneric()
            throws ReflectiveOperationException
    {
        assertInterface(
                LongFunction.class,
                lookup().findStatic(getClass(), "incrementAndPrint", methodType(String.class, long.class)),
                (print, wrapped) -> assertEquals(print.apply(1), "2"));
    }

    private static String incrementAndPrint(long x)
    {
        return String.valueOf(x + 1);
    }

    @Test
    public void testObjectAndDefaultMethods()
            throws ReflectiveOperationException
    {
        assertInterface(
                StringLength.class,
                lookup().findStatic(getClass(), "stringLength", methodType(int.class, String.class)),
                (length, wrapped) -> {
                    assertEquals(length.length("abc"), 3);
                    assertEquals(length.theAnswer(), 42);
                });
    }

    private static int stringLength(String s)
    {
        return s.length();
    }

    public interface StringLength
    {
        int length(String s);

        default int theAnswer()
        {
            return 42;
        }

        @Override
        String toString();
    }

    @Test
    public void testUncheckedException()
            throws ReflectiveOperationException
    {
        assertInterface(
                Runnable.class,
                lookup().findStatic(getClass(), "throwUncheckedException", methodType(void.class)),
                (runnable, wrapped) -> assertThatThrownBy(runnable::run)
                        .isInstanceOf(VerifyException.class));
    }

    private static void throwUncheckedException()
    {
        throw new VerifyException("unchecked");
    }

    @Test
    public void testCheckedException()
            throws ReflectiveOperationException
    {
        assertInterface(
                Runnable.class,
                lookup().findStatic(getClass(), "throwCheckedException", methodType(void.class)),
                (runnable, wrapped) -> {
                    if (wrapped) {
                        assertThatThrownBy(runnable::run)
                                .isInstanceOf(UndeclaredThrowableException.class)
                                .hasCauseInstanceOf(IOException.class);
                    }
                    else {
                        assertThatThrownBy(runnable::run)
                                .isInstanceOf(IOException.class);
                    }
                });
    }

    private static void throwCheckedException()
            throws IOException
    {
        throw new IOException("checked");
    }

    @Test
    public void testMutableCallSite()
            throws ReflectiveOperationException
    {
        MethodHandle one = lookup().findStatic(getClass(), "one", methodType(int.class));
        MethodHandle two = lookup().findStatic(getClass(), "two", methodType(int.class));

        MutableCallSite callSite = new MutableCallSite(methodType(int.class));
        assertInterface(
                IntSupplier.class,
                callSite.dynamicInvoker(),
                (supplier, wrapped) -> {
                    callSite.setTarget(one);
                    assertEquals(supplier.getAsInt(), 1);
                    callSite.setTarget(two);
                    assertEquals(supplier.getAsInt(), 2);
                });
    }

    private static int one()
    {
        return 1;
    }

    private static int two()
    {
        return 2;
    }

    private static <T> void assertInterface(Class<T> interfaceType, MethodHandle target, BiConsumer<T, Boolean> consumer)
    {
        consumer.accept(MethodHandleProxies.asInterfaceInstance(interfaceType, target), true);
        consumer.accept(FastMethodHandleProxies.asInterfaceInstance(interfaceType, target), true);
        consumer.accept(toInterfaceInstance(interfaceType, target), false);
    }

    @SuppressWarnings("unchecked")
    private static <T> T toInterfaceInstance(Class<T> type, MethodHandle target)
    {
        Method method = getSingleAbstractMethod(type);
        target = toDirectMethodHandle(target, type.getClassLoader());
        try {
            MethodHandle handle = lookup().unreflect(method);
            MethodType methodType = handle.type().dropParameterTypes(0, 1);
            Class<?> targetClass = lookup().revealDirect(target).getDeclaringClass();
            CallSite callSite = LambdaMetafactory.metafactory(
                    privateLookupIn(targetClass, lookup()),
                    method.getName(),
                    methodType(type),
                    methodType,
                    target,
                    methodType);
            return (T) callSite.getTarget().invoke();
        }
        catch (Throwable t) {
            throwIfUnchecked(t);
            throw new IllegalArgumentException(t);
        }
    }
}
