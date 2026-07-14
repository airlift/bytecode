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
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import static io.airlift.bytecode.Access.FINAL;
import static io.airlift.bytecode.Access.PUBLIC;
import static io.airlift.bytecode.Access.a;
import static io.airlift.bytecode.ClassGenerator.classGenerator;
import static io.airlift.bytecode.ParameterizedType.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAnnotationDefinition
{
    @Test
    void testListValidation()
    {
        AnnotationDefinition annotation = new AnnotationDefinition(StringValues.class);

        assertThatCode(() -> annotation.setValue("value", ImmutableList.of("a", "b")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> annotation.setValue("nested", ImmutableList.of(ImmutableList.of("a"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List contains a nested list");

        assertThatThrownBy(() -> annotation.setValue("invalid", ImmutableList.of(new Object())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List contains invalid type");

        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> annotation.setValue("null", withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List contains a null element");
    }

    @Test
    void testListAnnotationValue()
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/ListAnnotationExample",
                type(Object.class));
        classDefinition.declareDefaultConstructor(a(PUBLIC));
        classDefinition.declareAnnotation(StringValues.class)
                .setValue("value", ImmutableList.of("a", "b"));

        Class<?> clazz = classGenerator(getClass().getClassLoader())
                .defineClass(classDefinition, Object.class);

        StringValues annotation = clazz.getAnnotation(StringValues.class);
        assertThat(annotation.value()).containsExactly("a", "b");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface StringValues
    {
        String[] value();
    }
}
