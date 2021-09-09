package com.magustek.szjh.utils;

import com.alibaba.fastjson.JSONObject;
import java.util.*;

/**
 * @Author xww
 * @Date 2021/9/8 9:17 上午
 */
public class JsonParser {

    public static void iteraJsonOrArray(String source,Map resultMap) {
        if (source.indexOf(":") == -1) {
            return;
        }
        JSONObject fromObject = JSONObject.parseObject(source);
        Iterator keys = fromObject.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next().toString();
            Object value = fromObject.get(key);
            String val = value.toString();
            if (val.indexOf("[{") == -1) {
                //说明不存在数组json即格式为："[{" 开头的数据。可以允许是[10,11,12]的非json数组
                if (val.indexOf(":") == -1) {
                    resultMap.put(key, val);
                } else {
                    iteraJson(val, resultMap);
                }
            } else if (val.indexOf("[{") != -1) {
                //说明存在数组json即格式为：[{开头的json数组
                if (val.indexOf("[{") == 0) {
                    //说明当前value就是一个json数组
                    //去除[括号
                    String jsons = val.substring(1, val.lastIndexOf("]"));//得到数据格式为：{...},{...},{...}
                    //把上面得到jsons分割成数组
                    //因为数据格式为{name:joker,age:20},{...},{...}，所以不能用逗号分割。否则会变"{name:joker" "age:20}"
                    //使用正则表达式把},{替换成}|{
                    jsons = jsons.replaceAll("\\}\\s?,\\s?\\{", "}|{");
                    String[] split = jsons.split("\\|");
                    List list = new ArrayList();
                    Map tempMap = null;
                    for (int i = 0; i < split.length; i++) {
                        tempMap = new HashMap();
                        iteraJsonOrArray(split[i], tempMap);//符合当前递归条件
                        list.add(tempMap);
                    }
                    resultMap.put(key,list);
                } else {
                    //说明value可能是一个json，这个json中任然包含数组。例如：{inner:[{a:1,b:2,c:3}]}
                    iteraJsonOrArray(val, resultMap);//符合当前递归条件
                }
            }
        }
    }

    //递归遍历解析方法
    public static boolean iteraJson(String str,Map res){
        //因为json串中不一定有逗号，但是一定有：号，所以这里判断没有则说明value里没有json串了，可以直接加到res里
        if(str.indexOf(":") == -1){
            return true;
        }
        JSONObject fromObject = JSONObject.parseObject(str);
        Iterator keys = fromObject.keySet().iterator();
        while(keys.hasNext()){
            String key = keys.next().toString();
            Object value = fromObject.get(key);
            if(iteraJson(value.toString(),res)){
                res.put(key, value);
            }
        }
        return false;
    }
}
