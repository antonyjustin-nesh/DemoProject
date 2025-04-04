package com.nlevcluster.serialservice;

import com.nlevcluster.serialservice.IUartCallback;
// AIDL interface to provide UART data
interface IUartService {
    void writeData(in String data);
    void registerCallback(in IUartCallback callback);
    void unregisterCallback(in IUartCallback callback);
}
