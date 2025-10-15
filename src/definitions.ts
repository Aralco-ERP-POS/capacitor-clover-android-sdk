export interface PrintTextReceiptOptions {
  /**
   * Raw text to print. Use `\r\n` for new lines.
   */
  receipt: string;
}

export interface TakePaymentOptions {
  /**
   * Whole number of cents to charge (e.g. 1000 for $10.00).
   */
  amount: number;
  /**
   * Optional unique identifier for the transaction. Generated automatically if omitted.
   */
  uid?: string;
}

export interface RefundOptions {
  /**
   * Whole number of cents to refund (e.g. 1000 for $10.00).
   */
  amount: number;
  /**
   * Unique identifier associated with the original transaction.
   */
  uid: string;
}

export interface CloverSaleResponse {
  [key: string]: unknown;
}

export interface CloverRefundResponse {
  [key: string]: unknown;
}

export interface GetNewUIDResult {
  uid: string;
}

export interface IsCloverResult {
  isClover: boolean;
}

export interface StartScanResult {
  barcode: string;
}

export interface PrintTextReceiptResult {
  success: boolean;
}

export interface CloverAndroidSDKPlugin {
  /**
   * Optional: store the Clover remote application identifier (RAID).
   * The intent-based Payments API does not require this value.
   */
  setRemoteApplicationId(options: { remoteApplicationId: string }): Promise<{ remoteApplicationId: string }>;

  /**
   * Generate a new unique identifier for use with Clover transactions.
   */
  getNewUID(): Promise<GetNewUIDResult>;

  /**
   * Check whether the current device is a Clover device.
   */
  isClover(): Promise<IsCloverResult>;

  /**
   * Start a barcode scan using the Clover device scanner.
   */
  startScan(): Promise<StartScanResult>;

  /**
   * Print raw text on the default Clover receipt printer.
   */
  printTextReceipt(options: PrintTextReceiptOptions): Promise<PrintTextReceiptResult>;

  /**
   * Execute a payment on the Clover device.
   */
  takePayment(options: TakePaymentOptions): Promise<CloverSaleResponse>;

  /**
   * Issue a manual refund on the Clover device.
   */
  refund(options: RefundOptions): Promise<CloverRefundResponse>;
}
