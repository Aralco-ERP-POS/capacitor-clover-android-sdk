import { WebPlugin } from '@capacitor/core';
import type { CloverAndroidSDKPlugin, CloverRefundResponse, CloverSaleResponse, GetNewUIDResult, IsCloverResult, PrintTextReceiptOptions, PrintTextReceiptResult, RefundOptions, StartScanResult, TakePaymentOptions } from './definitions';
export declare class CloverAndroidSDKWeb extends WebPlugin implements CloverAndroidSDKPlugin {
    setRemoteApplicationId(_options: {
        remoteApplicationId: string;
    }): Promise<{
        remoteApplicationId: string;
    }>;
    getNewUID(): Promise<GetNewUIDResult>;
    isClover(): Promise<IsCloverResult>;
    startScan(): Promise<StartScanResult>;
    printTextReceipt(_options: PrintTextReceiptOptions): Promise<PrintTextReceiptResult>;
    takePayment(_options: TakePaymentOptions): Promise<CloverSaleResponse>;
    refund(_options: RefundOptions): Promise<CloverRefundResponse>;
}
