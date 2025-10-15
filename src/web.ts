import { WebPlugin } from '@capacitor/core';

import type {
  CloverAndroidSDKPlugin,
  CloverRefundResponse,
  CloverSaleResponse,
  GetNewUIDResult,
  IsCloverResult,
  PrintTextReceiptOptions,
  PrintTextReceiptResult,
  RefundOptions,
  StartScanResult,
  TakePaymentOptions,
} from './definitions';

export class CloverAndroidSDKWeb extends WebPlugin implements CloverAndroidSDKPlugin {
  async setRemoteApplicationId(_options: { remoteApplicationId: string }): Promise<{ remoteApplicationId: string }> {
    throw this.unimplemented('setRemoteApplicationId is not implemented on the web.');
  }
  async getNewUID(): Promise<GetNewUIDResult> {
    throw this.unimplemented('getNewUID is not implemented on the web.');
  }

  async isClover(): Promise<IsCloverResult> {
    throw this.unimplemented('isClover is not implemented on the web.');
  }

  async startScan(): Promise<StartScanResult> {
    throw this.unimplemented('startScan is not implemented on the web.');
  }

  async printTextReceipt(_options: PrintTextReceiptOptions): Promise<PrintTextReceiptResult> {
    throw this.unimplemented('printTextReceipt is not implemented on the web.');
  }

  async takePayment(_options: TakePaymentOptions): Promise<CloverSaleResponse> {
    throw this.unimplemented('takePayment is not implemented on the web.');
  }

  async refund(_options: RefundOptions): Promise<CloverRefundResponse> {
    throw this.unimplemented('refund is not implemented on the web.');
  }
}
