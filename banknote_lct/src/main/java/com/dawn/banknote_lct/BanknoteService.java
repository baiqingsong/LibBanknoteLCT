package com.dawn.banknote_lct;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.dawn.serial.LSerialUtil;

public class BanknoteService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    private final static int h_start_port = 0x01;
    private boolean autoGetStatus = false;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case h_start_port:
                    startPort(msg.arg1, autoGetStatus);
                    break;
            }
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
        Log.e("dawn", "BanknoteService onCreate");
        registerReceiver();
    }
    private BanknoteReceiverListener mListener;

    private int totalMoney = 0;//总收款
    private LSerialUtil mSerialUtil;
    private void startPort(int port, boolean auto_check_status){
        Log.e("dawn", "startPort port = " + port + ", auto_check_status = " + auto_check_status);
        if(mSerialUtil == null){
            mSerialUtil = new LSerialUtil(port, 9600, 8, 1, 'E', LSerialUtil.SerialType.TYPE_HEX, new LSerialUtil.OnSerialListener() {
                @Override
                public void startError() {
                    Log.e("dawn", "banknote startError");
                }

                @Override
                public void receiverError() {
                    Log.e("dawn", "banknote receiverError");
                }

                @Override
                public void sendError() {
                    Log.e("dawn", "banknote sendError");
                }

                @Override
                public void getReceiverStr(String str) {
                    if(TextUtils.isEmpty(str))
                        return;
                    Log.e("dawn", "banknote getReceiverStr str = " + str);
                    try{
                        str = str.toLowerCase();
                        if("808f".equals(str)){
                            //握手
                            sendMsg(BanknoteCommand.getStatusCommand());//握手回应02
                            if(mListener != null)
                                mListener.getStatus(true);
                        }else if("10".equals(str)){
                            //设备状态

                        }else if("3e".equals(str)){
                            //设备状态
                            if(mListener != null)
                                mListener.startMoneyStatus(true);

                        }else if("5e".equals(str)) {
                            //设备状态
                            if (mListener != null)
                                mListener.stopMoneyStatus(true);
                        }
                        else{
                            String moneyData = str;
                            if(str.contains("818f")){
                                moneyData = str.replace("818f", "");
                            }else if(str.contains("81")) {
                                moneyData = str.replace("81", "");
                            }else if(str.contains("2f")) {
                                moneyData = str.replace("2f", "");
                            }
                            int moneyInt = hexToInt(moneyData);
                            int errorStart = hexToInt("20");
                            int errorStop = hexToInt("2f");
                            if(moneyInt <= errorStop && moneyInt >= errorStart) {
                                //错误
                                String error = null;
                                switch (moneyData){
                                    case "20":
                                        error = "马达故障";
                                        break;
                                    case "21":
                                        error = "检验码故障";
                                        break;
                                    case "22":
                                        error = "卡币";
                                        break;
                                    case "23":
                                        error = "纸币移开";
                                        break;
                                    case "24":
                                        error = "纸箱移开";
                                        break;
                                    case "25":
                                        error = "电眼故障";
                                        break;
                                    case "27":
                                        error = "钓鱼";
                                        break;
                                    case "28":
                                        error = "纸箱故障";
                                        break;
                                    case "29":
                                        error = "拒收";
                                        break;
                                    case "2f":
                                        error = "异常情况结束";
                                        break;
                                }
                                if(mListener != null)
                                    mListener.receiverMoneyError(error);
                            }
                            int moneyStart = hexToInt("40");
                            int moneyStop = hexToInt("4f");
                            if(moneyInt >= moneyStart && moneyInt <= moneyStop) {
                                //收款
                                sendMsg(BanknoteCommand.getReceiverCommand());
                                int moneyIndex = moneyInt - moneyStart + 1;
                                if(mListener != null)
                                    mListener.receiverMoney(moneyIndex, totalMoney);
                            }
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                }
            });
            mListener = BanknoteFactory.getInstance(this).getListener();
        }
    }

    /**
     * 十六进制转十进制
     * @param hex 十六进制字符串
     * @return 十进制整数
     */
    private int hexToInt(String hex) {
        return Integer.parseInt(hex, 16);
    }

    /**
     * 发送信息
     */
    private void sendMsg(String msg) {
        Log.e("dawn", "sendMsg = " + msg);
        if (mSerialUtil != null && !TextUtils.isEmpty(msg))
            mSerialUtil.sendHexMsg(msg);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mReceiver != null){
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private BanknoteReceiver mReceiver;
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiver() {
        // Register your broadcast receiver here
        mReceiver = new BanknoteReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BanknoteConstant.RECEIVER_BANKNOTE);
        registerReceiver(mReceiver, intentFilter);
    }

    private class BanknoteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Handle the broadcast message here
            if(intent == null){
                return;
            }
            String command = intent.getStringExtra("command");
            if (command == null) {
                return;
            }
            switch (command){
                case "start_port"://开启串口号
                    int port = intent.getIntExtra("port", 0);
                    Message msg = new Message();
                    msg.what = h_start_port;
                    msg.arg1 = port;
                    mHandler.sendMessageDelayed(msg, 3000);
                    break;
                case "start_money"://开始收款
                    int money = intent.getIntExtra("money", 0);
                    if(money <= 0)
                        return;
                    totalMoney = money;
                    sendMsg(BanknoteCommand.getStartMoneyCommand());
                    if(mListener != null)
                        mListener.startMoneyStatus(true);
                    break;
                case "stop_money"://停止收款
                    totalMoney = 0;
                    sendMsg(BanknoteCommand.getStopMoneyCommand());
                    if(mListener != null)
                        mListener.stopMoneyStatus(true);
                    break;
            }
        }

    }
}
