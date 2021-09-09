package com.magustek.szjh;

import com.mongodb.DuplicateKeyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @Author xww
 * @Date 2021/9/8 10:34 上午
 */
@Slf4j
public class ReflectUtil {

    //调用对象指定方法，该方法不支持参数为空的方法调用
    public static Object invoke(Object object, String methodName, Class<?>[] classes, Object... args) {
        try {
            Method method = object.getClass().getDeclaredMethod(methodName, classes);
            return method.invoke(object, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof DuplicateKeyException) {
                throw (DuplicateKeyException)e.getCause();
            }
            log.error("ReflectUtil方法调用，调用{}的方法{}异常", object.getClass().getName(), methodName, e);
            //todo 这里需要throw自己定义的异常
        } catch (Exception e) {
            log.error("ReflectUtil方法调用，调用{}的方法{}异常", object.getClass().getName(), methodName, e);
            //todo 这里需要throw自己定义的异常
            //for example throw new MyException(MyExceptionType.QUERY_DATA_FAIL，"ReflectUtil方法调用,调用对象{0}的方法{1}：{2}失败"，object.getClass().getSimpleName, methodName, args);
        }
    }

    //通过setter方法设置对象指定字段的值
    public static void setField(Object data, String name, Object value) {
        PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(data.getClass(), name);
        if (propertyDescriptor == null) {
            log.error("ReflectUtil.setField方法调用。对象{}中不存在{}对应的setter方法", data.getClass().getSimpleName(), name);
            //todo 这里需要throw自己定义的异常
        }
        Method writeMethod = propertyDescriptor.getWriteMethod();
        if (writeMethod != null) {
            if (!Modifier.isPublic(writeMethod.getDeclaringClass().getModifiers())) {
                writeMethod.setAccessible(true);
            }
            try {
                writeMethod.invoke(data, value);
            } catch (Exception e) {
                log.error("ReflectUtil.setField调用，设置对象{}中字段{}的值为{}", data.getClass().getSimpleName(), name, value, e);
                //todo 这里需要throw自己定义的异常
            }
        }
    }

    public static boolean fieldExist(Object data, String name) {
        PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(data.getClass(), name);
        return propertyDescriptor != null;
    }

    //通过getter方法获取指定字段的值
 public static Object getField(Object data, String name) {
        PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(data.getClass(), name);
        Method readMethod = null;
        if (propertyDescriptor != null) {
            readMethod = propertyDescriptor.getReadMethod();
        }
        if (readMethod == null) {
            log.error("ReflectUtil.getField方法调用。对象{}中不存在{}对应的getter方法", data.getClass().getSimpleName(), name);
            //todo 这里需要throw自己定义的异常
        }
        try {
            if (!Modifier.isPublic(readMethod.getDeclaringClass().getModifiers())) {
                readMethod.setAccessible(true);
            }
            return readMethod.invoke(data);
        } catch (Exception e) {
            log.error("ReflectUtil.getField方法调用,获取对象{}中字段{}的值", data.getClass().getSimpleName(), name, e);
            //todo 这里需要throw自己定义的异常
        }
    }

    //获取目标的所有属性名称
    public static List<String> getProperties(Object data, String... ignoreProperties) {
        Class<?> dataClass = data.getClass();
        PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(dataClass);
        List<String> ignorePropertiesList = Arrays.asList(ignoreProperties);
        List<String> names = new ArrayList<>();
        for (PropertyDescriptor pd : propertyDescriptors) {
            if (!ignorePropertiesList.contains(pd.getName())) {
                names.add(pd.getName());
            }
        }
        return names;
    }

    public static Object updateData(Object update, Object data) {
        Field[] fields = data.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (!fieldExist(update, field.getName())) {
                continue;
            }
            Object updateField = getField(update, field.getName());
            if (updateField != null) {
                setField(data, field.getName(), updateField);
            }
        }
        return data;
    }

    public static <T extends Annotation> void findFieldAnnotation(Object data, Class<T> annotationClass, BiConsumer<T, Object> consumer) {
        Field[] fields = data.getClass().getFields();
        for (Field field : fields) {
            T annotation = field.getAnnotation(annotationClass);
            if (annotation == null) {
                continue;
            }
            boolean access = field.isAccessible();
            field.setAccessible(true);
            try {
                Object o = field.get(data);
                consumer.accept(annotation, o);
            } catch (IllegalAccessException e) {
                log.error("ReflectUtil.findFieldAnnotation方法调用,获取对象{}中字段{}的值", data.getClass().getSimpleName(), field.getName(), e);
                //todo 这里需要throw自己定义的异常
            }
            field.setAccessible(access);
        }
    }
}
