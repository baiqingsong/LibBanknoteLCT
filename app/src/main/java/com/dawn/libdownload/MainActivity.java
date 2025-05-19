package com.dawn.libdownload;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.dawn.banknote_lct.BanknoteFactory;
import com.dawn.banknote_lct.BanknoteReceiverListener;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BanknoteFactory.getInstance(this).setListener(new BanknoteReceiverListener() {
            @Override
            public void getStatus(boolean status) {
                Log.e("dawn", "getStatus status = " + status);
            }

            @Override
            public void startMoneyStatus(boolean status) {
                Log.e("dawn", "startMoneyStatus status = " + status);
            }

            @Override
            public void stopMoneyStatus(boolean status) {
                Log.e("dawn", "stopMoneyStatus status = " + status);
            }

            @Override
            public void receiverMoney(int receiverMoney, int totalMoney) {
                Log.e("dawn", "receiverMoney receiverMoney = " + receiverMoney + ", totalMoney = " + totalMoney);
            }

            @Override
            public void receiverMoneyError(String errorMsg) {
                Log.e("dawn", "receiverMoneyError errorMsg = " + errorMsg);
            }
        });
    }

    public void startPort(View view){
        // Start the port with the specified parameters
        BanknoteFactory.getInstance(this).startService(4);
    }

    public void startMoney(View view){
        // Start the money transaction with the specified parameters
        BanknoteFactory.getInstance(this).startMoney(5);
    }

    public void stopMoney(View view){
        // Stop the money transaction
        BanknoteFactory.getInstance(this).stopMoney();
    }
}