package gr.mybook.lunar_3;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.opencv.imgproc.Imgproc.drawContours;
import static org.opencv.imgproc.Imgproc.getRectSubPix;
import static org.opencv.imgproc.Imgproc.rectangle;

public class MainActivity extends Activity implements CvCameraViewListener2 {
    private static final String  TAG              = "myopencv";
    //Bluetooth variables
    protected static final byte FORWARD = 0x5;
    protected static final byte BACKWARD = 0x6;
    protected static final byte LEFT = 0x2;
    protected static final byte RIGHT = 0x3;
    protected static final byte STOP = 0x9;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE=1;

    // Messages sent to Handler from Threads
    public static final int MESSAGE_DEVICE_NAME = 2;
    public static final int MESSAGE_FIELD = 3;
    public static final int MESSAGE_READ = 4;
    public static final int MESSAGE_WRITE = 5;

    // Key names to Handler from Threads
    public static final String DEVICE_NAME = "device_name";
    public static final String FIELD = "field";
    public static final String READ = "read";
    public static final String WRITE = "write";

    // Return Intent extra
    public static String DEVICE_ADDRESS = "device_address";

    private BluetoothAdapter mBluetoothAdapter=null;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    //Camera variables
    private Mat mRgba, img_gray, window, erd, tgt, dest;
    private CameraBridgeViewBase mOpenCvCameraView;
    private List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
    private boolean train=false;


    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camerapreview);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
//        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgba = new Mat();
        img_gray = new Mat();
        window = new Mat();
        erd = new Mat();
        tgt = new Mat();
        dest = new Mat();
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        // convert to Gray scale
        Imgproc.cvtColor(mRgba, img_gray, Imgproc.COLOR_BGR2GRAY);
        // make it binary with threshold=100
        Imgproc.threshold(img_gray, erd, 100, 255, Imgproc.THRESH_OTSU);
        // remove pixel noise by "erode" with structuring element of 9X9
        Mat erode = Imgproc.getStructuringElement(Imgproc.MORPH_ERODE, new Size(9, 9));
        Imgproc.erode(erd, tgt, erode);
        // apply "dilation" to enlarge object
        Mat dilate = Imgproc.getStructuringElement(Imgproc.MORPH_DILATE, new Size(9, 9));
        Imgproc.dilate(tgt, erd, dilate);
        //take a window
        Size s=erd.size();
        int W = (int) s.width;
        int H = (int) s.height;
        Rect r=new Rect(0,H/2-100, W,200);
        Mat mask= new Mat(s, CvType.CV_8UC1, new Scalar(0,0,0));
        rectangle(mask, r.tl(), r.br(),new Scalar(255, 255, 255),-1);
        erd.copyTo(window, mask);

        // find the contours
        Imgproc.findContours(window, contours, dest, 0, 2);
        // find largest contour
        int maxContour = -1;
        double area = 0;
        for (int i = 0; i<contours.size(); i++) {
            double contArea = Imgproc.contourArea(contours.get(i));
            if (contArea > area) {
                area = contArea;
                maxContour = i;
            }
        }
        // form bounding rectangle for largest contour
        Rect rect = null;
        if (maxContour > -1)
            rect = Imgproc.boundingRect(contours.get(maxContour));
        //position to center
        while (!train) {
            if (rect != null) {
                Imgproc.rectangle(mRgba, rect.tl(), rect.br(), new Scalar(255, 255, 255), 5);
                if ((rect.x + rect.width / 2) > W/2-20 && (rect.x + rect.width / 2)< W/2+20) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "OK", Toast.LENGTH_SHORT).show();
                        }
                    });
                    train = true;
                }
            }
            if (contours != null)
                contours.clear();
            return mRgba;
        }
        if (train) {
            if (rect != null) {
                Imgproc.rectangle(mRgba, rect.tl(), rect.br(), new Scalar(255, 255, 255), 5);
                // direction of movement
                int thr = 100;
                if ((rect.x + rect.width / 2) < (W / 2 - thr)) {
                    // move to the RIGHT
                    uHandler.obtainMessage(MainActivity.LEFT).sendToTarget();
                } else {
                    if ((rect.x + rect.width / 2) > (W / 2 + thr)) {
                        uHandler.obtainMessage(MainActivity.RIGHT).sendToTarget();
                    } else {
                        uHandler.obtainMessage(MainActivity.FORWARD).sendToTarget();
                    }
                }
            }
            else {
                // stop moving
                uHandler.obtainMessage(MainActivity.STOP).sendToTarget();
            }
        }
        if (contours != null)
            contours.clear();
        return mRgba;
    }


    // The Handler that gets information back from myProcess
    private final Handler uHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            byte[] buffer = new byte[1];
            switch(msg.what) {
                case RIGHT:
                    buffer[0] = RIGHT;
                    break;
                case LEFT:
                    buffer[0] = LEFT;
                    break;
                case FORWARD:
                    buffer[0] = FORWARD;
                    break;
                case STOP:
                    buffer[0] = STOP;
                    break;
            }
            try {
                mConnectedThread.write(buffer);
            } catch(Exception e) {}
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras().getString(DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Cancel any thread currently running a connection
                    if (mConnectedThread != null) {
                        mConnectedThread.cancel();
                        mConnectedThread = null;
                    }
                    // Attempt to connect to the device
                    mConnectThread = new ConnectThread(device);
                    mConnectThread.start();
                }
        }
    }

    // Management of BT connection through ConnectedThread
    public void connected(BluetoothSocket socket, BluetoothDevice device) {

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    // Stop all threads
    public void stop() {

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    // Connection attempt failed
    private void connectionFailed() {

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_FIELD);
        Bundle bundle = new Bundle();
        bundle.putString(FIELD, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    // Connection was lost
    private void connectionLost() {

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_FIELD);
        Bundle bundle = new Bundle();
        bundle.putString(FIELD, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    // Thread for connection with remote BT device
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
            }
            mmSocket = tmp;
        }

        public void run() {

            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed();
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) { }
                return;
            }

            // Reset the ConnectThread-connection ok
            synchronized (MainActivity.this) {
                mConnectThread = null;
            }
            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    // Manage connection with BT device - in and out data
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;


        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    connectionLost();
                    break;
                }
            }
        }

        // Write to the connected OutStream
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, buffer.length, -1, buffer).sendToTarget();
            } catch (IOException e) { }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    // The Handler that gets information back
    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_WRITE:
                    break;
                case MESSAGE_READ:
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    break;
                case MESSAGE_FIELD:
                    Toast.makeText(MainActivity.this, msg.getData().getString(FIELD), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect:
                Intent intent = new Intent(this, DeviceSearchActivity.class);
                startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.menu_info:
                doInfo();
                return true;
        }
        return false;
    }

    private void doInfo() {
        new AlertDialog.Builder(this).
                setTitle( getString(R.string.title_info)).
                setMessage(" Constructor ==> John Ellinas\n"
                        + " Institution ==> TEI of Piraeus\n"
                        + " Laboratory ==> Applied Information Systems\n"
                        + " Project Name ==> Lunar\n"
                        + " Application ==> Follow the target\n").
                show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        uHandler.obtainMessage(MainActivity.STOP).sendToTarget();
    }
}
