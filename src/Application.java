import com.leotech.protocal.DataCenter;
import com.leotech.protocal.Protocol101;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class Application {
    public static void main(String[] args) {
        // 读取当前目录配置文件
        String host;
        int port;
        int callPeriod = 300;
        try {
            FileInputStream inputStream = new FileInputStream("config.properties");
            Properties prop = new Properties();
            prop.load(inputStream);
            host = prop.getProperty("host", "");
            if(host.equals("")) {
                throw new Exception("请配置远程主机IP地址");
            }
            port = Integer.parseInt(prop.getProperty("port", "2404"));
            callPeriod = Integer.parseInt(prop.getProperty("call_period", "300"));
        } catch (Exception e) {
            System.out.println("参数配置："+e.getMessage());
            return;
        }

        //
        Protocol101 app = new Protocol101(callPeriod, 0x01);
        app.init(host, port);
        app.start();
        // 启动定时器
        Timer timer = new Timer();
        // 1分钟后执行 每5分钟执行一次
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("-------开始保存断面数据");
                DataCenter.saveData();
                System.out.println("-------完成保存断面数据");
            }
        }, 10*1000, 5*60*1000);
    }
}
