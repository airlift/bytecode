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
import io.airlift.bytecode.fixture.IsolatedAdder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MutableCallSite;
import java.lang.ref.WeakReference;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFastMethodHandleProxies
{
    @Test
    void testBasic()
            throws ReflectiveOperationException
    {
        assertInterface(
                LongUnaryOperator.class,
                lookup().findStatic(getClass(), "increment", methodType(long.class, long.class)),
                addOne -> assertThat(addOne.applyAsLong(1)).isEqualTo(2L));
    }

    private static long increment(long x)
    {
        return x + 1;
    }

    @Test
    void testGeneric()
            throws ReflectiveOperationException
    {
        assertInterface(
                LongFunction.class,
                lookup().findStatic(getClass(), "incrementAndPrint", methodType(String.class, long.class)),
                print -> assertThat(print.apply(1)).isEqualTo("2"));
    }

    private static String incrementAndPrint(long x)
    {
        return String.valueOf(x + 1);
    }

    @Test
    void testObjectAndDefaultMethods()
            throws ReflectiveOperationException
    {
        assertInterface(
                StringLength.class,
                lookup().findStatic(getClass(), "stringLength", methodType(int.class, String.class)),
                length -> {
                    assertThat(length.length("abc")).isEqualTo(3);
                    assertThat(length.theAnswer()).isEqualTo(42);
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
    void testUncheckedException()
            throws ReflectiveOperationException
    {
        assertInterface(
                Runnable.class,
                lookup().findStatic(getClass(), "throwUncheckedException", methodType(void.class)),
                runnable -> assertThatThrownBy(runnable::run)
                        .isInstanceOf(VerifyException.class));
    }

    private static void throwUncheckedException()
    {
        throw new VerifyException("unchecked");
    }

    @Test
    void testCheckedException()
            throws ReflectiveOperationException
    {
        assertInterface(
                Runnable.class,
                lookup().findStatic(getClass(), "throwCheckedException", methodType(void.class)),
                runnable -> assertThatThrownBy(runnable::run)
                        .isInstanceOf(UndeclaredThrowableException.class)
                        .hasCauseInstanceOf(IOException.class));
    }

    private static void throwCheckedException()
            throws IOException
    {
        throw new IOException("checked");
    }

    @Test
    void testMutableCallSite()
            throws ReflectiveOperationException
    {
        MethodHandle one = lookup().findStatic(getClass(), "one", methodType(int.class));
        MethodHandle two = lookup().findStatic(getClass(), "two", methodType(int.class));

        MutableCallSite callSite = new MutableCallSite(methodType(int.class));
        assertInterface(
                IntSupplier.class,
                callSite.dynamicInvoker(),
                supplier -> {
                    callSite.setTarget(one);
                    assertThat(supplier.getAsInt()).isEqualTo(1);
                    callSite.setTarget(two);
                    assertThat(supplier.getAsInt()).isEqualTo(2);
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

    @Test
    void testHiddenClass()
            throws ReflectiveOperationException
    {
        MethodHandle target = lookup().findStatic(getClass(), "one", methodType(int.class));
        IntSupplier supplier = FastMethodHandleProxies.asInterfaceInstance(IntSupplier.class, target);

        Class<?> proxyClass = supplier.getClass();
        assertThat(proxyClass.isHidden()).isTrue();
        // a hidden class lives in the runtime package of its lookup class
        assertThat(proxyClass.getPackageName()).isEqualTo(FastMethodHandleProxies.class.getPackageName());
        // and is not discoverable by name
        assertThatThrownBy(() -> Class.forName(proxyClass.getName()))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void testClassNameIsUsedAsStem()
            throws ReflectiveOperationException
    {
        MethodHandle target = lookup().findStatic(getClass(), "one", methodType(int.class));
        IntSupplier supplier = FastMethodHandleProxies.asInterfaceInstance("MyProxy", IntSupplier.class, target);

        // the JVM appends "/0x..." to make the name unique, so the requested name is only a stem
        assertThat(supplier.getClass().getName())
                .startsWith(FastMethodHandleProxies.class.getPackageName() + ".MyProxy/");
    }

    @Test
    void testProxyClassIsUnloaded()
            throws ReflectiveOperationException, InterruptedException
    {
        MethodHandle target = lookup().findStatic(getClass(), "one", methodType(int.class));

        WeakReference<Class<?>> proxyClass = createProxyClassReference(target);

        // the proxy is unreachable, so its hidden class must become collectable
        for (int i = 0; i < 100 && proxyClass.get() != null; i++) {
            System.gc();
            Thread.sleep(10);
        }
        assertThat(proxyClass.get()).isNull();
    }

    private static WeakReference<Class<?>> createProxyClassReference(MethodHandle target)
    {
        IntSupplier supplier = FastMethodHandleProxies.asInterfaceInstance(IntSupplier.class, target);
        assertThat(supplier.getAsInt()).isEqualTo(1);
        return new WeakReference<>(supplier.getClass());
    }

    @Test
    void testNonPublicInterface()
            throws ReflectiveOperationException
    {
        MethodHandle target = lookup().findStatic(getClass(), "one", methodType(int.class));
        assertThatThrownBy(() -> FastMethodHandleProxies.asInterfaceInstance(PackagePrivate.class, target))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a public interface");
    }

    interface PackagePrivate
    {
        int value();
    }

    @Test
    void testNotAnInterface()
            throws ReflectiveOperationException
    {
        MethodHandle target = lookup().findStatic(getClass(), "one", methodType(int.class));
        assertThatThrownBy(() -> FastMethodHandleProxies.asInterfaceInstance(String.class, target))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a public interface");
    }

    @Test
    void testMultipleAbstractMethods()
            throws ReflectiveOperationException
    {
        MethodHandle target = lookup().findStatic(getClass(), "one", methodType(int.class));
        assertThatThrownBy(() -> FastMethodHandleProxies.asInterfaceInstance(TwoMethods.class, target))
                .isInstanceOf(IllegalArgumentException.class);
    }

    public interface TwoMethods
    {
        int first();

        int second();
    }

    /**
     * The lookup overload exists so that callers can proxy interfaces their own loader can
     * see but this library's cannot. The fixture package is reloaded in an isolated loader,
     * which yields an interface distinct from the one on the test classpath and unreachable
     * from the loader that defined {@link FastMethodHandleProxies}.
     */
    @Test
    void testInterfaceInvisibleToLibraryLoader()
            throws ReflectiveOperationException
    {
        IsolatedClassLoader isolated = new IsolatedClassLoader(getClass().getClassLoader(), "io.airlift.bytecode.fixture.");
        Class<?> isolatedAdder = isolated.loadClass("io.airlift.bytecode.fixture.IsolatedAdder");
        Lookup isolatedLookup = (Lookup) isolated.loadClass("io.airlift.bytecode.fixture.IsolatedLookup")
                .getMethod("lookup").invoke(null);

        // the isolated interface really is a different class than the one this test compiled against
        assertThat(isolatedAdder).isNotSameAs(IsolatedAdder.class);
        assertThat(isolatedAdder.getClassLoader()).isSameAs(isolated);
        // and the library's own loader resolves that name to a different class
        assertThat(FastMethodHandleProxies.class.getClassLoader().loadClass(isolatedAdder.getName()))
                .isNotSameAs(isolatedAdder);

        MethodHandle target = lookup().findStatic(getClass(), "add", methodType(int.class, int.class, int.class));
        Object proxy = FastMethodHandleProxies.asInterfaceInstance(isolatedLookup, "Adder", isolatedAdder, target);

        assertThat(isolatedAdder.isInstance(proxy)).isTrue();
        assertThat(proxy.getClass().isHidden()).isTrue();
        // the proxy is defined in the lookup's loader and package, which is how it sees the interface
        assertThat(proxy.getClass().getClassLoader()).isSameAs(isolated);
        assertThat(proxy.getClass().getPackageName()).isEqualTo("io.airlift.bytecode.fixture");
        assertThat(isolatedAdder.getMethod("add", int.class, int.class).invoke(proxy, 13, 42)).isEqualTo(55);
    }

    /**
     * The counterpart to {@link #testInterfaceInvisibleToLibraryLoader()}: without a caller
     * lookup the proxy lands in this library's loader, which cannot see the isolated interface.
     */
    @Test
    void testInterfaceInvisibleToLibraryLoaderIsRejected()
            throws ReflectiveOperationException
    {
        IsolatedClassLoader isolated = new IsolatedClassLoader(getClass().getClassLoader(), "io.airlift.bytecode.fixture.");
        Class<?> isolatedAdder = isolated.loadClass("io.airlift.bytecode.fixture.IsolatedAdder");

        MethodHandle target = lookup().findStatic(getClass(), "add", methodType(int.class, int.class, int.class));
        assertThatThrownBy(() -> FastMethodHandleProxies.asInterfaceInstance(isolatedAdder, target))
                .isInstanceOf(ClassCastException.class);
    }

    private static int add(int a, int b)
    {
        return a + b;
    }

    /**
     * Loads classes under a package prefix itself rather than delegating, so they are
     * distinct from, and invisible to, the classes the parent loader defines.
     */
    private static final class IsolatedClassLoader
            extends ClassLoader
    {
        private final String packagePrefix;

        private IsolatedClassLoader(ClassLoader parent, String packagePrefix)
        {
            super(parent);
            this.packagePrefix = packagePrefix;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException
        {
            if (!name.startsWith(packagePrefix)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = defineClass(name, readClassBytes(name));
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private Class<?> defineClass(String name, byte[] bytes)
        {
            return defineClass(name, bytes, 0, bytes.length);
        }

        private byte[] readClassBytes(String name)
                throws ClassNotFoundException
        {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream input = getParent().getResourceAsStream(resource)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                return input.readAllBytes();
            }
            catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    private static <T> void assertInterface(Class<T> interfaceType, MethodHandle target, Consumer<T> consumer)
    {
        consumer.accept(MethodHandleProxies.asInterfaceInstance(interfaceType, target));
        consumer.accept(FastMethodHandleProxies.asInterfaceInstance(interfaceType, target));
    }
}
