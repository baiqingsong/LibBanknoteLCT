package com.dawn.banknote_lct;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BanknoteFactory {
    //单例模式
    private static BanknoteFactory instance;
    private static Context mContext;
    private BanknoteFactory(Context context) {
        this.mContext = context;
    }
    public static BanknoteFactory getInstance(Context context) {
        if (instance == null) {
            synchronized (BanknoteFactory.class) {
                if (instance == null) {
                    instance = new BanknoteFactory(context);
                }
            }
        }
        return instance;
    }

    private BanknoteReceiverListener mBanknoteReceiverListener;
    public BanknoteReceiverListener getListener() {
        return mBanknoteReceiverListener;
    }

    public void setListener(BanknoteReceiverListener listener) {
        this.mBanknoteReceiverListener = listener;
    }

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 0:
                    startPort();
                    break;
            }
        }
    };

    private int serialPort = 0;//串口号

    public void startService(int port) {
        serialPort = port;
        mContext.startService(new Intent(mContext, BanknoteService.class));
        mHandler.sendEmptyMessageDelayed(0, 5000);
    }


    public void startPort() {
        Intent intent = new Intent(BanknoteConstant.RECEIVER_BANKNOTE);
        intent.putExtra("command", "start_port");
        intent.putExtra("port", serialPort);
        mContext.sendBroadcast(intent);
    }

    public void startMoney(int money){
        Intent intent = new Intent(BanknoteConstant.RECEIVER_BANKNOTE);
        intent.putExtra("command", "start_money");
        intent.putExtra("money", money);
        mContext.sendBroadcast(intent);
    }

    public void stopMoney(){
        Intent intent = new Intent(BanknoteConstant.RECEIVER_BANKNOTE);
        intent.putExtra("command", "stop_money");
        mContext.sendBroadcast(intent);
    }
}
