import { registerPlugin } from '@capacitor/core';

import type { CloverAndroidSDKPlugin } from './definitions';

const CloverAndroidSDK = registerPlugin<CloverAndroidSDKPlugin>('CloverAndroidSDK', {
  web: () => import('./web').then(m => new m.CloverAndroidSDKWeb()),
});

export * from './definitions';
export { CloverAndroidSDK };
