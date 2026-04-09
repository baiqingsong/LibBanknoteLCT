package com.dawn.banknote_lct;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.dawn.serial.LSerialUtil;

/**
 * 纸钞机管理类
 * <p>
 * 通过串口与LCT纸钞机通信，支持握手、收款、停止等操作。
 * 使用 HandlerThread 在后台线程进行串口通信，回调在主线程执行。
 * </p>
 * <p>
 * 用法：
 * <pre>
 *   BanknoteManager manager = BanknoteManager.getInstance(context);
 *   manager.setListener(listener);
 *   manager.startPort(4);
 *   manager.startMoney(100);
 *   manager.stopMoney();
 *   manager.destroy(); // 不再使用时释放资源
 * </pre>
 * </p>
 */
public class BanknoteManager {

    private static final String TAG = "BanknoteManager";
    private static final int BAUD_RATE = 9600;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;
    private static final char PARITY = 'E';

    /** 纸钞机运行状态 */
    public enum State {
        /** 未初始化 */
        IDLE,
        /** 正在连接（串口已打开，等待握手） */
        CONNECTING,
        /** 已连接（握手成功） */
        CONNECTED,
        /** 正在收款 */
        RECEIVING
    }

    private static volatile BanknoteManager sInstance;

    private final Context mContext;
    private final Handler mMainHandler;
    private final Object mLock = new Object();

    private HandlerThread mSerialThread;
    private Handler mSerialHandler;
    private volatile BanknoteReceiverListener mListener;
    private LSerialUtil mSerialUtil;
    private volatile int mTotalMoney;
    private volatile State mState = State.IDLE;
    private int mCurrentPort;
    private boolean mDestroyed;

    private BanknoteManager(Context context) {
        mContext = context.getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取单例实例
     *
     * @param context 上下文（内部使用 ApplicationContext，不会泄漏 Activity）
     */
    public static BanknoteManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (BanknoteManager.class) {
                if (sInstance == null) {
                    sInstance = new BanknoteManager(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * 设置纸钞机事件监听器
     */
    public void setListener(BanknoteReceiverListener listener) {
        mListener = listener;
    }

    /**
     * 移除纸钞机事件监听器
     */
    public void removeListener() {
        mListener = null;
    }

    /**
     * 获取当前状态
     */
    public State getState() {
        return mState;
    }

    /**
     * 查询串口是否已连接（握手完成）
     */
    public boolean isConnected() {
        State state = mState;
        return state == State.CONNECTED || state == State.RECEIVING;
    }

    /**
     * 打开串口连接纸钞机
     *
     * @param port 串口号
     */
    public void startPort(int port) {
        if (port < 0) {
            Log.w(TAG, "Invalid port number: " + port);
            return;
        }
        synchronized (mLock) {
            if (mDestroyed) {
                Log.w(TAG, "Manager already destroyed");
                return;
            }
            if (mState != State.IDLE) {
                Log.w(TAG, "Cannot start port, current state: " + mState);
                return;
            }
            mCurrentPort = port;
            mState = State.CONNECTING;
        }
        ensureSerialThread();
        mSerialHandler.post(() -> openPort(port));
    }

    /**
     * 重新连接串口（先断开再重新打开）
     */
    public void reconnect() {
        synchronized (mLock) {
            if (mDestroyed) {
                Log.w(TAG, "Manager already destroyed");
                return;
            }
            final int port = mCurrentPort;
            if (port < 0) {
                Log.w(TAG, "No port configured, call startPort() first");
                return;
            }
            mState = State.CONNECTING;
            mTotalMoney = 0;
            ensureSerialThread();
            mSerialHandler.post(() -> {
                closePort();
                openPort(port);
            });
        }
    }

    /**
     * 断开串口连接（不销毁实例，可再次 startPort）
     */
    public void disconnect() {
        synchronized (mLock) {
            mTotalMoney = 0;
            mState = State.IDLE;
        }
        if (mSerialHandler != null) {
            mSerialHandler.post(this::closePort);
        }
    }

    /**
     * 开始收款
     *
     * @param money 目标收款金额（必须大于0）
     */
    public void startMoney(int money) {
        if (money <= 0) {
            Log.w(TAG, "Invalid money amount: " + money);
            return;
        }
        if (!isConnected()) {
            Log.w(TAG, "Not connected, cannot start money. State: " + mState);
            return;
        }
        mTotalMoney = money;
        postSendMsg(BanknoteCommand.getStartMoneyCommand());
    }

    /**
     * 停止收款
     */
    public void stopMoney() {
        if (!isConnected()) {
            Log.w(TAG, "Not connected, cannot stop money. State: " + mState);
            return;
        }
        mTotalMoney = 0;
        postSendMsg(BanknoteCommand.getStopMoneyCommand());
    }

    /**
     * 释放所有资源，销毁单例。
     * 调用后需要重新通过 {@link #getInstance(Context)} 获取新实例。
     */
    public void destroy() {
        synchronized (mLock) {
            mDestroyed = true;
            mState = State.IDLE;
            mTotalMoney = 0;
            mListener = null;
        }
        // 在串口线程上同步关闭串口，再退出线程
        if (mSerialThread != null) {
            if (mSerialHandler != null) {
                mSerialHandler.post(() -> {
                    closePort();
                    mSerialThread.quitSafely();
                });
            } else {
                mSerialThread.quitSafely();
            }
            mSerialThread = null;
            mSerialHandler = null;
        }
        mMainHandler.removeCallbacksAndMessages(null);
        synchronized (BanknoteManager.class) {
            sInstance = null;
        }
    }

    // ===================== 内部方法 =====================

    private void ensureSerialThread() {
        if (mSerialThread == null || !mSerialThread.isAlive()) {
            mSerialThread = new HandlerThread("BanknoteSerial");
            mSerialThread.start();
            mSerialHandler = new Handler(mSerialThread.getLooper());
        }
    }

    private void openPort(int port) {
        if (mSerialUtil != null) {
            return;
        }
        Log.d(TAG, "Opening serial port: " + port);
        try {
            mSerialUtil = new LSerialUtil(port, BAUD_RATE, DATA_BITS, STOP_BITS, PARITY,
                    LSerialUtil.SerialType.TYPE_HEX, new LSerialUtil.OnSerialListener() {
                        @Override
                        public void onOpenError(String portPath, Exception e) {
                            Log.e(TAG, "Serial port open error: " + portPath, e);
                            synchronized (mLock) {
                                mState = State.IDLE;
                            }
                            notifyOnMainThread(() -> {
                                BanknoteReceiverListener l = mListener;
                                if (l != null) {
                                    l.onConnected(false);
                                }
                            });
                        }

                        @Override
                        public void onReceiveError(Exception e) {
                            Log.e(TAG, "Serial receive error", e);
                        }

                        @Override
                        public void onSendError(Exception e) {
                            Log.e(TAG, "Serial send error", e);
                        }

                        @Override
                        public void onDataReceived(String data) {
                            if (TextUtils.isEmpty(data)) {
                                return;
                            }
                            Log.d(TAG, "Received: " + data);
                            handleReceivedData(data.toLowerCase());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Failed to create serial connection", e);
            synchronized (mLock) {
                mState = State.IDLE;
            }
            notifyOnMainThread(() -> {
                BanknoteReceiverListener l = mListener;
                if (l != null) {
                    l.onConnected(false);
                }
            });
        }
    }

    /**
     * 处理从纸钞机接收的数据
     */
    private void handleReceivedData(String data) {
        try {
            // 检查固定响应码
            switch (data) {
                case BanknoteCommand.RESPONSE_HANDSHAKE:
                    sendMsg(BanknoteCommand.getStatusCommand());
                    synchronized (mLock) {
                        if (mState == State.CONNECTING) {
                            mState = State.CONNECTED;
                        }
                    }
                    notifyOnMainThread(() -> {
                        BanknoteReceiverListener l = mListener;
                        if (l != null) {
                            l.onConnected(true);
                        }
                    });
                    return;
                case BanknoteCommand.RESPONSE_DEVICE_STATUS:
                    return;
                case BanknoteCommand.RESPONSE_START_MONEY:
                    synchronized (mLock) {
                        mState = State.RECEIVING;
                    }
                    notifyOnMainThread(() -> {
                        BanknoteReceiverListener l = mListener;
                        if (l != null) {
                            l.onStartMoney(true);
                        }
                    });
                    return;
                case BanknoteCommand.RESPONSE_STOP_MONEY:
                    synchronized (mLock) {
                        if (mState == State.RECEIVING) {
                            mState = State.CONNECTED;
                        }
                    }
                    notifyOnMainThread(() -> {
                        BanknoteReceiverListener l = mListener;
                        if (l != null) {
                            l.onStopMoney(true);
                        }
                    });
                    return;
                default:
                    break;
            }

            // 提取数据载荷（去除帧头）
            String payload = extractPayload(data);
            if (TextUtils.isEmpty(payload)) {
                Log.w(TAG, "Unknown data format: " + data);
                return;
            }

            int value = parseHex(payload);
            if (value < 0) {
                Log.w(TAG, "Invalid hex payload: " + payload);
                return;
            }

            // 错误码范围: 0x20 - 0x2F
            if (value >= BanknoteCommand.ERROR_RANGE_START
                    && value <= BanknoteCommand.ERROR_RANGE_END) {
                String errorMsg = BanknoteCommand.getErrorMessage(payload);
                Log.w(TAG, "Banknote error [0x" + payload + "]: " + errorMsg);
                notifyOnMainThread(() -> {
                    BanknoteReceiverListener l = mListener;
                    if (l != null) {
                        l.onError(errorMsg);
                    }
                });
                return;
            }

            // 收款码范围: 0x40 - 0x4F
            if (value >= BanknoteCommand.MONEY_RANGE_START
                    && value <= BanknoteCommand.MONEY_RANGE_END) {
                sendMsg(BanknoteCommand.getReceiverCommand());
                int moneyIndex = value - BanknoteCommand.MONEY_RANGE_START + 1;
                int total = mTotalMoney;
                notifyOnMainThread(() -> {
                    BanknoteReceiverListener l = mListener;
                    if (l != null) {
                        l.onMoneyReceived(moneyIndex, total);
                    }
                });
                return;
            }

            Log.w(TAG, "Unhandled data value: 0x" + payload);
        } catch (Exception e) {
            Log.e(TAG, "Error processing received data", e);
        }
    }

    /**
     * 从原始数据中提取有效载荷，去除帧头
     * 帧格式：818f + payload 或 81 + payload 或 单独的 payload
     */
    private String extractPayload(String data) {
        if (data.startsWith("818f")) {
            return data.substring(4);
        }
        if (data.startsWith("81")) {
            return data.substring(2);
        }
        // 单字节/双字节响应，数据本身即为载荷
        if (data.length() <= 4) {
            return data;
        }
        return null;
    }

    /**
     * 安全的十六进制字符串转整数
     *
     * @return 转换结果，失败返回 -1
     */
    private int parseHex(String hex) {
        if (TextUtils.isEmpty(hex)) {
            return -1;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 将发送操作投递到串口线程执行，确保线程安全
     */
    private void postSendMsg(String msg) {
        if (mSerialHandler != null) {
            mSerialHandler.post(() -> sendMsg(msg));
        }
    }

    /**
     * 在串口线程中发送数据（仅内部调用，已确保在串口线程）
     */
    private void sendMsg(String msg) {
        if (mSerialUtil != null && !TextUtils.isEmpty(msg)) {
            Log.d(TAG, "Sending: " + msg);
            mSerialUtil.sendHex(msg);
        }
    }

    private void closePort() {
        if (mSerialUtil != null) {
            try {
                mSerialUtil.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error closing serial port", e);
            }
            mSerialUtil = null;
        }
    }

    /**
     * 确保回调在主线程执行
     */
    private void notifyOnMainThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mMainHandler.post(action);
        }
    }
}
