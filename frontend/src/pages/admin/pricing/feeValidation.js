/** FR-PRC-007: fees are decimals >= 0 with at most 2 places, at most 1,000,000.00 (mirrors the server). */
export const MAX_FEE = 1000000;

/** Returns an error message, or null when the text is an acceptable amount. */
export function validateFee(value) {
  const text = value == null ? '' : String(value).trim();
  if (text === '') return 'Enter an amount';
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return 'Use a number of 0 or more with at most 2 decimals';
  if (Number(text) > MAX_FEE) return 'The maximum is 1,000,000.00';
  return null;
}

/** Shows a server amount as an editable text with two decimals. Display only, no calculation. */
export function toInputValue(amount) {
  if (amount === null || amount === undefined || amount === '') return '';
  return Number(amount).toFixed(2);
}

/** Amount text to the JSON number sent to the server. */
export function toRequestAmount(text) {
  return Number(String(text).trim());
}
