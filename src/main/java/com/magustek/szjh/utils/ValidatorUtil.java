package com.magustek.szjh.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;


import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @Author xww
 * @Date 2021/9/8 10:07 上午
 */
public class ValidatorUtil {
    public static <T> String checkValid(T value) {
        //引入校验工具
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        //获取校验器
        Validator validator = factory.getValidator();
        List<String> list = new ArrayList<>();
        Set<ConstraintViolation<T>> violationSet = validator.validate(value);
        violationSet.forEach(violat -> {
            list.add(violat.getPropertyPath() + " " + violat.getMessage());
        });
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        return StringUtils.join(list,",");
    }
}
