package com.leotech.protocal;

public class YxParser {
    public static void parseYx(byte[] recvBytes) {
        System.out.println("-------解析遥信数据...");
        // 获取遥信数
        byte vsq = recvBytes[8];
        byte yxCount = (byte)(vsq & 0x7F);

        // 获取地址
        short startPoint = (short)(((recvBytes[14]&0x00FF) << 8) | (0x00FF & recvBytes[13]));

        for(byte i=0; i<yxCount; ++i) {
            short curPoint = (short)(startPoint + i);
            byte value = recvBytes[15+i];
            DataCenter.setYxDataMap(curPoint, value);
        }
    }
    public static void parseYxWithTime(byte[] receiveBytes) {

    }
}
