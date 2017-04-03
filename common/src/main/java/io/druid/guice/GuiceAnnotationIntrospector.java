/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.guice;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotationMap;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import com.metamx.common.IAE;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;

/**
 */
public class GuiceAnnotationIntrospector extends NopAnnotationIntrospector
{
  @Override
  public Object findInjectableValueId(AnnotatedMember m)
  {
    if (m.getAnnotation(JacksonInject.class) == null) {
      return null;
    }
    HashMap<Class<? extends Annotation>,Annotation> annotations;
    try {
      Field field = getField(m.getClass(), "_annotations");
      AnnotationMap map = (AnnotationMap) field.get(m);
      field = getField(map.getClass(), "_annotations");
      annotations = (HashMap<Class<? extends Annotation>,Annotation>) field.get(map);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    Annotation guiceAnnotation = null;
    for (Annotation annotation : annotations.values()) {
      if (annotation.annotationType().isAnnotationPresent(BindingAnnotation.class)) {
        guiceAnnotation = annotation;
        break;
      }
    }

    if (guiceAnnotation == null) {
      if (m instanceof AnnotatedMethod) {
        throw new IAE("Annotated methods don't work very well yet...");
      }
      return Key.get(m.getGenericType());
    }
    return Key.get(m.getGenericType(), guiceAnnotation);
  }

  private static Field getField(Class<?> type, String fieldName) {
    for (Field f : Arrays.asList(type.getDeclaredFields())) {
      f.setAccessible(true);
      if (f.getName().equals(fieldName)) {
        return f;
      }
    }

    if (type.getSuperclass() != null) {
      return getField(type.getSuperclass(), fieldName);
    }
    return null;
  }
}
