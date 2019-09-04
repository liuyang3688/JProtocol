import com.leotech.protocal.Protocol101;

public class Application {
    public static void main(String[] args) {
        Protocol101 app = new Protocol101(0x01);
        app.init();
        app.start();
    }
}
