import { WebPlugin } from '@capacitor/core';
export class CloverAndroidSDKWeb extends WebPlugin {
    async setRemoteApplicationId(_options) {
        throw this.unimplemented('setRemoteApplicationId is not implemented on the web.');
    }
    async getNewUID() {
        throw this.unimplemented('getNewUID is not implemented on the web.');
    }
    async isClover() {
        throw this.unimplemented('isClover is not implemented on the web.');
    }
    async startScan() {
        throw this.unimplemented('startScan is not implemented on the web.');
    }
    async printTextReceipt(_options) {
        throw this.unimplemented('printTextReceipt is not implemented on the web.');
    }
    async takePayment(_options) {
        throw this.unimplemented('takePayment is not implemented on the web.');
    }
    async refund(_options) {
        throw this.unimplemented('refund is not implemented on the web.');
    }
}
//# sourceMappingURL=web.js.map