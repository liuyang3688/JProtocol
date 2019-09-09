package com.leotech.protocal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Protocol101 extends Thread {
    final static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd@HH:mm:ss");
    static int callCount = 0;
    public enum CmdType {
        CMD101_NO,
        CMD101_RESSTS,
        CMD101_RESLINK,
        CMD101_RESINITACK,
        CMD101_RESCALLACK,
        CMD101_RESCALLOVERACK,
        CMD101_RESDATAACK,
        CMD101_RESTEST,
        CMD101_REQLINK,
        CMD101_RSTLINK,
        CMD101_ENDINIT,
        CMD101_REQCALL,
        CMD101_REQLEVEL1DATA,
        CMD101_REQLEVEL2DATA,
        CMD101_ACTIVECALL,
        CMD101_CALLSIG,
        CMD101_CALLMETER,
        CMD101_ENDCALL,
        CMD101_SOE
    };
    static int connectCount = 0;
    int address;
    int callPeriod;
    IecClient client = null;
    byte[] receiveBytes;
    byte[] sendBytes;

    CmdType currCommand = CmdType.CMD101_REQLINK;
    boolean needSendCommand = true;
    int step = 0;
    boolean running = true;
    Timer timer = null;

    public Protocol101(int callPeriod, int address)
    {
        receiveBytes = new byte[10240];
        sendBytes = new byte[10240];
        this.address = address;
        this.needSendCommand = true;
        this.timer = new Timer();
        this.callPeriod = callPeriod;
    }

    @Override
    public void run()
    {
        while (running)
        {
            if(needSendCommand)
            {
                sendPacket(sendBytes);
            } else {
                int len = client.recv(receiveBytes);
                if(len == 0) {
                    cycleConnect();
                } else {
                    parsePacket(receiveBytes, len);
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public void reqCall() {
        this.currCommand = CmdType.CMD101_REQCALL;
        this.needSendCommand = true;
    }

    public void startCallTimer() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                reqCall();
            }
        }, 1000, callPeriod);
    }

    public void stopCallTimer() {
        this.timer.cancel();
    }

    public void init(String host, int port)
    {
        client = new IecClient(host, port);
        client.connect();
        cycleConnect();
    }

    public void cycleConnect() {
        while(!client.isConnected()) {
            try{
                Thread.sleep(5000);
                System.out.println("\n客户端尝试第"+(++connectCount)+"次连接 " + client.getHost()+":"+client.getPort());
                client.connect();
                needSendCommand = true;
                currCommand = CmdType.CMD101_REQLINK;
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void parsePacket(byte[] receiveBytes, int len)
    {
        int processLen, leftLen;

        if (receiveBytes[0] == (byte)0x10) processLen = 6;
        else processLen = (receiveBytes[1]&0xff) + 6;
        leftLen = len;

        if (processLen <= leftLen)
        {
            if (!CrcUtil.calcCrcOk(receiveBytes, processLen))
            {
                return;
            }

            if (processLen == 6)
            {
                byte dir = (byte)(receiveBytes[1] & 0x80);
                byte pem = (byte)(receiveBytes[1] & 0x40);
                if ( dir != 0 && pem == 0)
                { //收到子站响应帧
                    switch(receiveBytes[1]&0x0f)
                    { // 功能码
                        case 0x0b: // 收到请求链路响应 发送复位请求
                            currCommand = CmdType.CMD101_RSTLINK;
                            needSendCommand = true;
                            break;
                        case 0x00: // 收到复位链路响应 等待子站启动
                            switch (currCommand) {
                                case CMD101_RSTLINK:
                                    // 此时需要翻转请求链路
                                    needSendCommand = false;
                                    break;
                            }
                            break;
                        case 0x02: // 测试链路响应
                            //currCommand = CmdType.CMD101_RESTEST;
                            needSendCommand = false;
                    }
                }
                else if(dir != 0 && pem != 0)
                {//收到子站启动帧
                    switch(receiveBytes[1]&0x0f)
                    { // 功能码
                        case 0x09: // 收到子站请求链路请求 发送确认
                            currCommand = CmdType.CMD101_RESSTS;
                            needSendCommand = true;
                            break;
                        case 0x00: // 收到子站复位链路请求 发送确认
                            currCommand = CmdType.CMD101_RESLINK;
                            needSendCommand = true;
                            break;
                        case 0x02: // 测试链路响应
                            currCommand = CmdType.CMD101_RESTEST;
                            needSendCommand = true;
                            break;
                    }
                }
                else
                {
                    System.out.println("解析固定帧长度");
                }
            } else if(processLen == 18) {
                byte ti = receiveBytes[7];
                byte vsq = receiveBytes[8];
                byte cot = receiveBytes[9];
                if(ti==0x46 && vsq==0x01 && cot==0x04) { // 初始化完成
                    // 发送2级数据召唤
                    currCommand = CmdType.CMD101_RESINITACK;
                    needSendCommand = true;
                } else if(ti==0x64 && vsq==0x01 && cot==0x07) { //收到总召确认
                    currCommand = CmdType.CMD101_RESCALLACK;
                    needSendCommand = true;
                } else if(ti==0x64 && vsq==0x01 && cot==0x0A) { //收到总召结束
                    currCommand = CmdType.CMD101_RESCALLOVERACK;
                    needSendCommand = true;
                }
            } else if(processLen > 18) { // 总召数据帧
                byte ti = receiveBytes[7];
                byte vsq = receiveBytes[8];
                byte cot = receiveBytes[9];
                switch(ti) {
                    case 0x01: // 单点信息
                        YxParser.parseYx(receiveBytes);
                        currCommand = CmdType.CMD101_RESDATAACK;
                        needSendCommand = true;
                        break;
                    case 0x02: // 带时标的单点
                        break;
                    case 0x0D: // 短浮点遥测
                        YcParser.parseFloatYc(receiveBytes);
                        currCommand = CmdType.CMD101_RESDATAACK;
                        needSendCommand = true;
                        break;
                    case 0x0E: // 带时标的短浮点遥测
                        break;
                }
            } else {
                System.out.println("接收帧格式非法");
            }
        }
    }

    public void sendPacket(byte[] sendBytes)
    {
        int sendLen = 0;

        switch (currCommand)
        {
            case CMD101_RESINITACK:
                sendLen = makeResponseConfirmPacket(sendBytes);
                //currCommand = CmdType.CMD101_REQLEVEL1DATA;
                currCommand = CmdType.CMD101_REQCALL;
                needSendCommand = true;
                break;
            case CMD101_RESSTS: //
                sendLen = makeResponseLinkStatusPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_RESLINK:
            case CMD101_RESTEST:
            case CMD101_RESCALLACK:
            case CMD101_RESDATAACK:
                sendLen = makeResponseConfirmPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_RESCALLOVERACK:
                sendLen = makeResponseConfirmPacket(sendBytes);
                break;
            case CMD101_REQLINK:
                sendLen = makeRequestLinkStatusPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_RSTLINK:
                sendLen = makeRequestResetLinkPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_ENDINIT:
                sendLen = makeEndInitPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_REQCALL:
                System.out.println(">>>>>>>发起第"+(++callCount)+"次总召 时间："+formatter.format(new Date()));
                sendLen = makeRequestCallPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_REQLEVEL1DATA:
                sendLen = makeRequestLevel1DataPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_REQLEVEL2DATA:
                sendLen = makeRequestLevel2DataPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_ACTIVECALL:
                sendLen = makeActiveCallPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_CALLSIG:
                sendLen = makeCallSignalPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_CALLMETER:
                sendLen = makeCallMeterPacket(sendBytes);
                needSendCommand = false;
                break;
            case CMD101_ENDCALL:
                sendLen = makeEndCallPacket(sendBytes);
                needSendCommand = false;
                break;
            default:
                currCommand = CmdType.CMD101_NO;
                needSendCommand = false;
                break;
        }

        if (sendLen != 0)
        {
            client.send(sendBytes, sendLen);
        }
        if(currCommand == CmdType.CMD101_RESCALLOVERACK) {
            try{
                System.out.println("<<<<<<<完成第"+callCount+"次总召 时间："+formatter.format(new Date()));
                Thread.sleep(30000);
            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                currCommand = CmdType.CMD101_REQCALL;
                needSendCommand = true;
            }
        }
    }


    //响应链路状态
    private int makeResponseLinkStatusPacket(byte[] byteArr)
    {
        byteArr[0] = 0x10;
        byteArr[1] = (byte)0x0b; //11 响应链路状态
        byteArr[2] = (byte)address;
        byteArr[3] = (byte)(address>>8);
        byteArr[4] = CrcUtil.calc(byteArr, 1,4);
        byteArr[5] = 0x16;
        return 6;
    }

    //响应肯定确认
    private int makeResponseConfirmPacket(byte[] byteArr)
    {
        byteArr[0] = 0x10;
        byteArr[1] = (byte)0x00; //肯定确认
        byteArr[2] = (byte)address;
        byteArr[3] = (byte)(address>>8);
        byteArr[4] = CrcUtil.calc(byteArr, 1,4);
        byteArr[5] = 0x16;
        return 6;
    }

    //请求链路状态
    private int makeRequestLinkStatusPacket(byte[] byteArr)
    {
        byteArr[0] = 0x10;
        byteArr[1] = (byte)0x49; //9 请求链路状态
        byteArr[2] = (byte)address;
        byteArr[3] = (byte)(address>>8);
        byteArr[4] = CrcUtil.calc(byteArr, 1,4);
        byteArr[5] = 0x16;
        return 6;
    }

    //请求复位链路
    private int makeRequestResetLinkPacket(byte[] byteArr)
    {
        byteArr[0] = 0x10;
        byteArr[1] = (byte)0x40; //0 复位链路状态
        byteArr[2] = (byte)address;
        byteArr[3] = (byte)(address>>8);
        byteArr[4] = CrcUtil.calc(byteArr, 1,4);
        byteArr[5] = 0x16;
        return 6;
    }

    //结束初始化
    private int makeEndInitPacket(byte[] byteArr)
    {
        byteArr[0] = 0x68;
        byteArr[1] = 0x0c;
        byteArr[2] = 0x0c;
        byteArr[3] = 0x68;
        byteArr[4] = (byte)0xf3;
        byteArr[5] = (byte)address;
        byteArr[6] = (byte)(address>>8);
        byteArr[7] = 0x46; //初始化结束
        byteArr[8] = 0x01; //可变结构限定词
        byteArr[9] = 0x04; //原因：初始化
        byteArr[10] = 0x00;
        byteArr[11] = (byte)address;
        byteArr[12] = (byte)(address>>8);
        byteArr[13] = 0x00; //信息对象低位地址
        byteArr[14] = 0x00; //信息对象高位地址
        byteArr[15] = 0x00; //信息元素
        byteArr[16] = CrcUtil.calc(byteArr, 4,16);
        byteArr[17] = 0x16;
        return 18;
    }

    //请求总召
    private int makeRequestCallPacket(byte[] byteArr)
    {
        byteArr[0] = 0x68;
        byteArr[1] = 0x0c;
        byteArr[2] = 0x0c;
        byteArr[3] = 0x68;
        byteArr[4] = (byte)0x73;
        byteArr[5] = (byte)address;
        byteArr[6] = (byte)(address>>8);
        byteArr[7] = 0x64; //总召
        byteArr[8] = 0x01; //可变结构限定词
        byteArr[9] = 0x06; //原因：请求
        byteArr[10] = 0x00;
        byteArr[11] = (byte)address;
        byteArr[12] = (byte)(address>>8);
        byteArr[13] = 0x00; //信息对象低位地址
        byteArr[14] = 0x00; //信息对象高位地址
        byteArr[15] = 0x14;
        byteArr[16] = CrcUtil.calc(byteArr, 4,16);
        byteArr[17] = 0x16;
        return 18;
    }
    //请求1级数据
    private int makeRequestLevel1DataPacket(byte[] byteArr) {
        byteArr[0] = 0x10;
        byteArr[1] = (byte)0x7A; //0 复位链路状态
        byteArr[2] = (byte)address;
        byteArr[3] = (byte)(address>>8);
        byteArr[4] = CrcUtil.calc(byteArr, 1,4);
        byteArr[5] = 0x16;
        return 6;
    }
    //请求2级数据
    private int makeRequestLevel2DataPacket(byte[] byteArr) {
        byteArr[0] = 0x10;
        byteArr[1] = (byte)0x7B; //0 复位链路状态
        byteArr[2] = (byte)address;
        byteArr[3] = (byte)(address>>8);
        byteArr[4] = CrcUtil.calc(byteArr, 1,4);
        byteArr[5] = 0x16;
        return 6;
    }
    //召唤激活确认
    private int makeActiveCallPacket(byte[] byteArr)
    {
        byteArr[0] = 0x68;
        byteArr[1] = 0x0c;
        byteArr[2] = 0x0c;
        byteArr[3] = 0x68;
        byteArr[4] = (byte)0xf3;
        byteArr[5] = (byte)address;
        byteArr[6] = (byte)(address>>8);
        byteArr[7] = 0x64; //应用报文类型 100代表站召唤命令
        byteArr[8] = 0x01; //可变结构限定词 离散排列 数量1个
        byteArr[9] = 0x07; //传送原因 7代表激活确认
        byteArr[10] = 0x00;
        byteArr[11] = (byte)address;
        byteArr[12] = (byte)(address>>8);
        byteArr[13] = 0x00; //信息对象低位地址
        byteArr[14] = 0x00; //信息对象高位地址
        byteArr[15] = 0x14; //总召唤
        byteArr[16] = CrcUtil.calc(byteArr, 4,16);
        byteArr[17] = 0x16;
        return 18;
    }

    //遥信
    private int makeCallSignalPacket(byte[] byteArr)
    {
        return 0;
//        //一次上传10个设备遥信，每个设备10个遥信点号
//        int startDeviceNo, endDeviceNo, startDotNo, totalDots;
//        int signal;
//        startDeviceNo = 10 * (step -1) + 1;
//        startDotNo = 10 * (startDeviceNo - 1) + 1;
//        endDeviceNo = 10 * step;
//        if (endDeviceNo > Const.maxdeviceNo)
//        {
//            endDeviceNo = Const.maxdeviceNo;
//        }
//        totalDots = (endDeviceNo - startDeviceNo + 1) * 10;
//
//        byteArr[0] = 0x68;
//        byteArr[1] = (byte)(totalDots+11);
//        byteArr[2] = (byte)(totalDots+11);
//        byteArr[3] = 0x68;
//        byteArr[4] = (byte)0xf3;
//        byteArr[5] = (byte)address;
//        byteArr[6] = (byte)(address>>8);
//        byteArr[7] = 0x01; //应用报文类型 单点遥信
//        byteArr[8] = (byte)(0x80 + totalDots); //可变结构限定词 顺序排列
//        byteArr[9] = 0x14; //传送原因 20代表响应站召唤
//        byteArr[10] = 0x00;
//        byteArr[11] = (byte)address;
//        byteArr[12] = (byte)(address>>8);
//        byteArr[13] = (byte)startDotNo; //信息对象低位地址
//        byteArr[14] = (byte)(startDotNo>>8); //信息对象高位地址
//        for (int i = startDeviceNo, loc = 0; i < endDeviceNo; i++, loc++)
//        {
//            if ((BaseStore.deviceIds[i] != null) && BaseStore.idDeviceStatusMap.containsKey(BaseStore.deviceIds[i]))
//            {
//                signal = BaseStore.idDeviceStatusMap.get(BaseStore.deviceIds[i]).getSignal();
//                for (int j = 0; j < 10; j++)
//                {
//                    if ((signal & (1<<j)) == 0)
//                    {
//                        byteArr[15+loc*10+j] = 0x00;
//                    }
//                    else
//                    {
//                        byteArr[15+loc*10+j] = 0x01;
//                    }
//                }
//            }
//            else
//            {
//                for (int j = 0; j < 10; j++)
//                {
//                    byteArr[15+loc*10+j] = 0x00;
//                }
//            }
//
//        }
//        byteArr[totalDots+15] = CrcUtil.iec101CrcCalc(byteArr, totalDots+17);
//        byteArr[totalDots+16] = 0x16;
//        return totalDots+17;
    }

    //遥测
    private int makeCallMeterPacket(byte[] byteArr)
    {
        return 0;
//        //一次上传8个设备遥测，每个设备5个遥测点号
//        int startDeviceNo, endDeviceNo, startDotNo, totalDots;
//        float[] meterArray;
//        int iVal;
//        startDeviceNo = 8 * (step -1) + 1;
//        startDotNo = 8 * (startDeviceNo - 1) + 1 + 0x4000;
//        endDeviceNo = 8 * step;
//        if (endDeviceNo > Const.maxdeviceNo)
//        {
//            endDeviceNo = Const.maxdeviceNo;
//        }
//        totalDots = (endDeviceNo - startDeviceNo + 1) * 5;
//
//        byteArr[0] = 0x68;
//        byteArr[1] = (byte)(totalDots*5+11);
//        byteArr[2] = (byte)(totalDots*5+11);
//        byteArr[3] = 0x68;
//        byteArr[4] = (byte)0xf3;
//        byteArr[5] = (byte)address;
//        byteArr[6] = (byte)(address>>8);
//        byteArr[7] = 0x0D; //应用报文类型  短浮点遥测
//        byteArr[8] = (byte)(0x80 + totalDots); //可变结构限定词 顺序排列
//        byteArr[9] = 0x14; //传送原因 20代表响应站召唤
//        byteArr[10] = 0x00;
//        byteArr[11] = (byte)address;
//        byteArr[12] = (byte)(address>>8);
//        byteArr[13] = (byte)startDotNo; //信息对象低位地址
//        byteArr[14] = (byte)(startDotNo>>8); //信息对象高位地址
//        for (int i = startDeviceNo, loc = 0; i < endDeviceNo; i++)
//        {
//            if ((BaseStore.deviceIds[i] != null) && BaseStore.idDeviceStatusMap.containsKey(BaseStore.deviceIds[i]))
//            {
//                meterArray = BaseStore.idDeviceStatusMap.get(BaseStore.deviceIds[i]).getMeterArr();
//            }
//            else
//            {
//                meterArray = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
//
//            }
//            for (int j = 0; j < 5; j++)
//            {
//                iVal = Float.floatToIntBits(meterArray[j]);
//                byteArr[15+loc*5] = (byte)iVal;
//                byteArr[16+loc*5] = (byte)(iVal>>8);
//                byteArr[17+loc*5] = (byte)(iVal>>16);
//                byteArr[18+loc*5] = (byte)(iVal>>24);
//                byteArr[19+loc*5] = (byte)iVal;
//                loc++;
//            }
//
//        }
//        byteArr[totalDots*5+15] = CrcUtil.calc(byteArr, totalDots*5+17);
//        byteArr[totalDots*5+16] = 0x16;
//        return totalDots*5+17;
    }


    //召唤结束
    private int makeEndCallPacket(byte[] byteArr)
    {
        byteArr[0] = 0x68;
        byteArr[1] = 0x0c;
        byteArr[2] = 0x0c;
        byteArr[3] = 0x68;
        byteArr[4] = (byte)0xf3;
        byteArr[5] = (byte)address;
        byteArr[6] = (byte)(address>>8);
        byteArr[7] = 0x64; //应用报文类型 100代表站召唤命令
        byteArr[8] = 0x01; //可变结构限定词 离散排列 数量1个
        byteArr[9] = 0x0a; //传送原因 10代表激活终止
        byteArr[10] = 0x00;
        byteArr[11] = (byte)address;
        byteArr[12] = (byte)(address>>8);
        byteArr[13] = 0x00; //信息对象低位地址
        byteArr[14] = 0x00; //信息对象高位地址
        byteArr[15] = 0x14; //总召唤
        byteArr[16] = CrcUtil.calc(byteArr, 4,16);
        byteArr[17] = 0x16;
        return 18;
    }
}
