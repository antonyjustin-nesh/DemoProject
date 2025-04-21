package com.nlevcluster.serialservice;
// Callback interface to send UART data updates
interface IUartCallback {
    void onRawFeedReceived(String data);
    void onProcessedFeedReceived(String data);
}