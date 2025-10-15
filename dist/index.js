import { registerPlugin } from '@capacitor/core';
const CloverAndroidSDK = registerPlugin('CloverAndroidSDK', {
    web: () => import('./web').then(m => new m.CloverAndroidSDKWeb()),
});
export * from './definitions';
export { CloverAndroidSDK };
//# sourceMappingURL=index.js.map