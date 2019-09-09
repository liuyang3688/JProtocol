package com.leotech.protocal;

import javafx.util.Pair;

import java.util.concurrent.ConcurrentHashMap;

public class DataCenter {
    private static ConcurrentHashMap<Integer, Byte> yxDataMap = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Integer, Float> ycDataMap = new ConcurrentHashMap<>();

    public static void setYxDataMap(int point, byte value) {
        yxDataMap.put(point, value);
    }
    public static void setYcDataMap(int point, float value) {
        ycDataMap.put(point, value);
    }
    public static void saveData() {
        // 打开文件
        System.out.println("-------保存遥信数据");
        // 遍历遥信
        for(Integer key : yxDataMap.keySet()) {
            byte value = yxDataMap.get(key);
            System.out.println("遥信点：" + key + " 0x"+Integer.toHexString(key)+" 值："+value);
        }
        // 遍历遥测
        System.out.println("-------保存遥测数据");
        for(Integer key : ycDataMap.keySet()) {
            float value = ycDataMap.get(key);
            System.out.println("遥测点：" + key + " 0x"+Integer.toHexString(key)+" 值："+value);
        }
    }
}
