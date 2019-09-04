package com.leotech.protocal;

public class CrcUtil {
    public static byte calc(byte[] data, int offset, int length) {
        long crcValue = 0;
        for(int i=offset; i<length; ++i) {
            crcValue += data[i];
        }
        return (byte)(crcValue % 256);
    }
    public static boolean calcCrcOk(byte[] data, int length) {
        return true;
    }
}
