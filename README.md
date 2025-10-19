# Capacitor Clover Android SDK

Capacitor plugin that bridges core capabilities of the Clover Android SDK, including payment collection, manual refunds, receipt printing, and barcode scanning.

## Installation

```bash
npm install capacitor-clover-android-sdk
npx cap sync
```

## API

### TypeScript / ES Module

```ts
import { CloverAndroidSDK } from 'capacitor-clover-android-sdk';

async function demo() {
  const { uid } = await CloverAndroidSDK.getNewUID();
  const { isClover } = await CloverAndroidSDK.isClover();

  if (!isClover) {
    throw new Error('Not running on a Clover device');
  }

  // Amounts are in cents (500 = $5.00)
  await CloverAndroidSDK.takePayment({ amount: 500, uid: uid });
}

demo().catch(console.error);
```

### Plain JavaScript (Capacitor WebView console)

```js
const plugins = window.Capacitor && window.Capacitor.Plugins;
if (!plugins || !plugins.CloverAndroidSDK) {
  throw new Error('Clover plugin not available');
}
const CloverAndroidSDK = plugins.CloverAndroidSDK;

CloverAndroidSDK.getNewUID()
  .then(({ uid }) => CloverAndroidSDK.takePayment({ amount: 500, uid }))
  .then(result => console.log('payment success', result.payment))
  .catch(err => console.error('payment failed', err));
```

### Listening for hardware barcode scans

```ts
import { CloverAndroidSDK } from 'capacitor-clover-android-sdk';

const handle = await CloverAndroidSDK.addListener('barcodeScanned', ({ barcode, type }) => {
  console.log('Barcode', barcode, 'type', type);
});

// later, when you're done listening
await handle.remove();
```

Hardware-triggered scans (for example, holding the left side button on a Clover Flex) emit this event even if you did not call `startScan()`.

Refer to `src/definitions.ts` for full type definitions.
