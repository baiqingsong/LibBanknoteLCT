package com.dawn.banknote_lct;

public interface BanknoteReceiverListener {
    void getStatus(boolean status);//纸钞机状态
    void startMoneyStatus(boolean status);//开始收款
    void stopMoneyStatus(boolean status);//停止收款
    void receiverMoney(int moneyIndex, int totalMoney);//收款金额
    void receiverMoneyError(String errorMsg);//收款错误
}
