package com.leotech.protocal;

public class YcParser {
    public static void parseFloatYc(byte[] recvBytes) {
        System.out.println("-------解析遥测数据...");
        // 获取遥信数
        byte vsq = recvBytes[8];
        byte ycCount = (byte)(vsq & 0x7F);

        // 获取地址
        short startPoint = (short)(((recvBytes[14]&0x00FF) << 8) | (0x00FF & recvBytes[13]));

        for(byte i=0; i<ycCount; ++i) {
            short curPoint = (short)(startPoint + i);
            int accum  = recvBytes[15 + i*5] & 0xFF;
            accum |= (recvBytes[15 + i*5 + 1]&0xFF) << 8;
            accum |= (recvBytes[15 + i*5 + 2]&0xFF) << 16;
            accum |= (recvBytes[15 + i*5 + 3]&0xFF) << 24;
            DataCenter.setYcDataMap(curPoint, Float.intBitsToFloat(accum));
        }
    }
    public static void parseFloatYcWithTime(byte[] receiveBytes) {

    }
}
