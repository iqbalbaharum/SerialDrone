package animatronic.devcon.co.serialdrone.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.nio.ByteBuffer;

public class RCService extends Service {

    private final static String TAG = "RCSERVICE";

    public final static String KEY_DATA = "animatronic.devcon.co.serialdrone.KEY_DATA";
    private final static int SEND_RAW_DATA_INTERVAL = 50;

    private UsbService usbService;
    private byte[] mLoopedData;

    private Handler sendRawDataTask = new Handler();

    public static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;

    private ByteBuffer mOutputBuffer = ByteBuffer.allocate(DEFAULT_READ_BUFFER_SIZE);

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            Log.d(TAG, "Bind");

            usbService = ((UsbService.UsbBinder) arg1).getService();
            if(mLoopedData.length > 0) {
                sendRawDataRunnable.run();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Intent bindingIntent = new Intent(this, UsbService.class);
        bindService(bindingIntent, usbConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // clear buffer
        mOutputBuffer.clear();

        unbindService(usbConnection);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mLoopedData = intent.getByteArrayExtra(KEY_DATA);

        // stop service when there is no data
        if(mLoopedData.length <= 0) {
            stopSelf();
        }

        return START_STICKY;
    }

    private final Runnable sendRawDataRunnable = new Runnable() {
        @Override
        public void run() {

            if(UsbService.SERVICE_CONNECTED) {
                // insert
                usbService.write(mLoopedData);
                sendRawDataTask.postDelayed(sendRawDataRunnable, SEND_RAW_DATA_INTERVAL);
            }
        }
    };


}
