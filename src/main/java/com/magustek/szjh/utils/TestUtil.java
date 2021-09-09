package com.magustek.szjh.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;

/**
 * @Author xww
 * @Date 2021/9/8 2:29 下午
 */
@Slf4j
public class TestUtil {
    public static String prefixOfGen = "";

    //默认匹配日期格式
    private static String[] TIME_PARSE_PATTENS = new String[]{
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd HH:mm",
            "yyyy-m-dd H:mm:ss",
            "yyyy/m/dd H:mm:ss",
            "yyyy-m-dd H:mm",
            "yyyy/m/dd H:mm",
            "yyyy-MM-dd HH:mm:ss:SSS",
            "yyyyMMddHHmmss"
    };

    @FunctionalInterface
    public interface RunnableInterface {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface RunnableReturnInterface<T> {
        T run() throws Exception;
    }

    public static void catchException(RunnableInterface runnable) {
        boolean hasException = false;
        try {
            runnable.run();
        } catch (Throwable th) {
            log.error("流程正常", th);
            hasException = true;
        } finally {
            Assert.isTrue(hasException, "流程错误");
        }
    }

    public static <T> T catchNoException(RunnableReturnInterface<T> runnable) {
        try {
            return runnable();
        } catch (Throwable th) {
            Assert.isTrue(th != null, "流程错误");
            //todo 抛自定义的exception
        }
    }

    public static void catchNoException(RunnableInterface runnable) {
        catchNoException(() -> {
            runnable.run();
            return null;
        });
    }

    //转换为对应的包装类型
    private static Class toWrapperType(Class clazz) {
        if (!clazz.isPrimitive()) {
            return clazz; //如果不是基本类型，则直接返回
        }
        if (clazz == short.class) {
            return Short.class;
        }
        if (clazz == int.class) {
            return Integer.class;
        }
        if (clazz == long.class) {
            return Long.class;
        }
        if (clazz == float.class) {
            return Float.class;
        }
        if (clazz == double.class) {
            return Double.class;
        }
        if (clazz == boolean.class) {
            return Boolean.class;
        }
        if (clazz == char.class) {
            return Character.class;
        }
        if (clazz == byte.class) {
            return Byte.class;
        }
        return clazz;
    }

    private static <T> T genValue(Class<T> clazz, String defaultValue) {
        try {
            //字符串
            if (ClassUtils.isAssignable(String.class, clazz)) {
                if (defaultValue != null) {
                    return (T) defaultValue;
                }
                return (T) (prefixOfGen + RandomStringUtils.random(8, true, true));
            }
            //布尔值
            if (ClassUtils.isAssignable(Boolean.class, clazz)) {
                if (defaultValue != null) {
                    return (T) Boolean.valueOf(defaultValue);
                }
                return (T) Boolean.valueOf(true);
            }
            //日期类型
            if (ClassUtils.isAssignable(Date.class, clazz)) {
                if (defaultValue != null) {
                    try {
                        return (T) DateUtils.parseDate(defaultValue, TIME_PARSE_PATTENS);
                    } catch (ParseException e) {
                        return null;
                    }
                }
                return (T) new Date();
            }
            //数字类型，默认值为10
            clazz = toWrapperType(clazz);
            if (ClassUtils.isAssignable(Number.class, clazz)) {
                if (defaultValue == null) {
                    defaultValue = "10";
                }
                //BigDecimal的父类是Number
                if (ClassUtils.isAssignable(BigDecimal.class, clazz)) {
                    return (T) new BigDecimal(defaultValue);
                }
                Method method = clazz.getMethod("valueOf", String.class);
                if (method != null) {
                    return (T) method.invoke(null, defaultValue);
                }
            }
        } catch (Throwable th) {
            throw new FatalBeanException("生成类型" + clazz.getName() + "的值失败。默认值是：" + defaultValue, th);
        }
        return null;
    }

    public static <T> T genValue(Class<T> clazz) {
        return genValue(clazz, null);
    }

    public static String genString() {
        return genValue(String.class);
    }

    public static void traverseFields(Class clazz, Function<Field, Boolean> function) {
        Class tempClass = clazz;
        //当父类为null时，说明到达了最上层的父类（Object类）
        while (tempClass != null) {
            for (Field field : tempClass.getDeclaredFields()) {
                Boolean flag = function.apply(field);
                if (!flag) {
                    break;
                }
            }
            //得到父类，然后赋值给自己
            tempClass = tempClass.getSuperclass();
        }
    }

    public static <T> T genFillData(Class<T> clazz, String... properties) {
        T val = genValue(clazz);
        if (val != null) {
            return val;
        }
        T result = BeanUtils.instantiateClass(clazz);
        //指定填充属性或者指定空属性
        Map<String, String> propertyMap = new HashMap<>(properties.length);
        if (properties != null) {
            for (String prop : properties) {
                int index = prop.indexOf(":");
                if (index < 0) {
                    propertyMap.put(prop, null);
                } else {
                    propertyMap.put(prop.substring(0, index).trim(), prop.substring(index + 1, prop.length()).trim());
                }
            }
        }
        traverseFields(clazz, field -> {
            String name = field.getName();
            String defaultValue = propertyMap.get(name);
            if (defaultValue == null && propertyMap.containsKey(name)) {
                return true;
            }
            try {
                Object value;
                if (List.class.isAssignableFrom(field.getType())) {
                    //获取list字段的泛型参数
                    ParameterizedType listGenericType = (ParameterizedType) field.getGenericType();
                    Type type = listGenericType.getActualTypeArguments()[0];
                    //默认List中只生成一个元素
                    value = Arrays.asList(genFillData((Class<?>) type));
                } else {
                    value = genValue(field.getType(),defaultValue);
                }
                if (value == null) {
                    return true;
                }
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    return true;
                }
                field.setAccessible(true);
                field.set(result, value);
                return true;
            } catch (Exception e) {
                throw new FatalBeanException("填充属性" + name + "的属性失败", e);
            }
        });
        return result;
    }





}
