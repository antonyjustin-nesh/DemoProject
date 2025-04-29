package com.nlevcluster.data_processor;
// Callback interface to send UART data updates
interface IUartCallback {
    void onRawFeedReceived(String data);
    void onProcessedFeedReceived(String data);
}