package com.dawn.banknote_lct;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BanknoteFactory {
    //单例模式
    private static BanknoteFactory instance;
    private static Context mContext;
    private BanknoteFactory(Context context) {
        this.mContext = context;
        context.startService(new Intent(context, BanknoteService.class));
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


    public void startPort(int port) {
        Intent intent = new Intent(BanknoteConstant.RECEIVER_BANKNOTE);
        intent.putExtra("command", "start_port");
        intent.putExtra("port", port);
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
