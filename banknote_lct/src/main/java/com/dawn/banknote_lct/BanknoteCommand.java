package com.dawn.banknote_lct;

public class BanknoteCommand {
    /**
     * 获取状态指令
     * @return 状态指令
     */
    public static String getStatusCommand(){
        return "02";
    }

    /**
     * 接收纸钞
     * @return 接收纸钞指令
     */
    public static String getReceiverCommand(){
        return "02";
    }

    /**
     * 拒收纸钞
     * @return 拒收纸钞指令
     */
    public static String getRejectCommand(){
        return "0f";
    }

    /**
     * 开启收款指令
     * @return 开启收款指令
     */
    public static String getStartMoneyCommand(){
        return "02";
    }

    /**
     * 停止收款指令
     * @return 停止收款指令
     */
    public static String getStopMoneyCommand(){
        return "0f";
    }
}
